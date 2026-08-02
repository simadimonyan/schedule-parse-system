package app.controller.api;

import app.repository.models.dto.api.workschedule.WorkScheduleRequest;
import app.repository.models.dto.api.workschedule.WorkScheduleResponse;
import app.repository.models.dto.mappers.WorkScheduleMapper;
import app.security.AdminAccess;
import app.service.domain.workschedule.WorkScheduleService;
import app.service.infra.MasterDirectoryService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * График занятости преподавателей: окна, в которые им можно ставить пары.
 *
 * <p>Чтение — по ФИО, как и везде в сервисе; запись — идентификатором справочника. Пустой
 * график означает «ограничений нет»: заполнять его на всех преподавателей не требуется.
 *
 * <p>Правка требует административного токена в заголовке {@code X-Admin-Token}.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/work-schedules")
@SecurityRequirement(name = "Authorization")
public class WorkScheduleController {

    private final WorkScheduleService workScheduleService;
    private final WorkScheduleMapper workScheduleMapper;
    private final MasterDirectoryService masterDirectoryService;
    private final AdminAccess adminAccess;

    public WorkScheduleController(
            WorkScheduleService workScheduleService,
            WorkScheduleMapper workScheduleMapper,
            MasterDirectoryService masterDirectoryService,
            AdminAccess adminAccess
    ) {
        this.workScheduleService = workScheduleService;
        this.workScheduleMapper = workScheduleMapper;
        this.masterDirectoryService = masterDirectoryService;
        this.adminAccess = adminAccess;
    }

    @GetMapping
    public ResponseEntity<WorkScheduleResponse.Envelope> list(
            @RequestParam(value = "teacher", required = false) String teacherLabel
    ) {
        log.info("GET Запрос: /api/v1/work-schedules?teacher={}", teacherLabel);

        Long teacherMasterId = teacherLabel == null ? null : masterDirectoryService.teacherId(teacherLabel);
        if (teacherLabel != null && teacherMasterId == null) {
            log.info("Преподаватель {} не найден в справочнике — графика нет", teacherLabel);
            return ResponseEntity.ok(workScheduleMapper.toEnvelope(List.of()));
        }

        return ResponseEntity.ok(workScheduleMapper.toEnvelope(workScheduleService.list(teacherMasterId)));
    }

    @PostMapping
    public ResponseEntity<WorkScheduleResponse> create(
            @RequestBody WorkScheduleRequest request,
            @RequestHeader(value = AdminAccess.HEADER, required = false) String adminToken
    ) {
        log.info("POST Запрос: /api/v1/work-schedules \n Body:\n {}", request);
        adminAccess.require(adminToken, "создание графика занятости");
        return ResponseEntity.ok(workScheduleMapper.toResponse(workScheduleService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<WorkScheduleResponse> update(
            @PathVariable("id") Long id,
            @RequestBody WorkScheduleRequest request,
            @RequestHeader(value = AdminAccess.HEADER, required = false) String adminToken
    ) {
        log.info("PUT Запрос: /api/v1/work-schedules/{}", id);
        adminAccess.require(adminToken, "правка графика занятости " + id);
        return ResponseEntity.ok(workScheduleMapper.toResponse(workScheduleService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(
            @PathVariable("id") Long id,
            @RequestHeader(value = AdminAccess.HEADER, required = false) String adminToken
    ) {
        log.info("DELETE Запрос: /api/v1/work-schedules/{}", id);
        adminAccess.require(adminToken, "удаление графика занятости " + id);

        workScheduleService.delete(id);
        return ResponseEntity.ok("График занятости удалён");
    }

}
