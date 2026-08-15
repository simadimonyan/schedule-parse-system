package app.controller.api;

import app.repository.models.dto.api.change.ChangeRequest;
import app.repository.models.dto.api.change.ChangeResponse;
import app.repository.models.dto.mappers.ChangeMapper;
import app.security.AdminAccess;
import app.service.domain.change.ChangeService;
import app.service.infra.MasterDirectoryService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

import java.time.LocalDate;
import java.util.List;

/**
 * Изменения расписания: переносы, отмены, замены, разовые пары.
 *
 * <p>Чтение спрашивают по названию группы или ФИО преподавателя — так же, как само
 * расписание: клиент знает записи по именам, идентификаторы справочника ему неизвестны.
 * Запись, наоборот, идёт идентификаторами: изменение заводит администратор, у которого
 * группа уже выбрана из списка, и разбирать по имени там нечего.
 *
 * <p>Правка требует административного токена в заголовке {@code X-Admin-Token}.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/schedule/changes")
@SecurityRequirement(name = "Authorization")
public class ChangeController {

    private final ChangeService changeService;
    private final ChangeMapper changeMapper;
    private final MasterDirectoryService masterDirectoryService;
    private final AdminAccess adminAccess;

    public ChangeController(
            ChangeService changeService,
            ChangeMapper changeMapper,
            MasterDirectoryService masterDirectoryService,
            AdminAccess adminAccess
    ) {
        this.changeService = changeService;
        this.changeMapper = changeMapper;
        this.masterDirectoryService = masterDirectoryService;
        this.adminAccess = adminAccess;
    }

    @GetMapping
    @PreAuthorize("hasRole('ROLE_SCHEDULE')")
    public ResponseEntity<ChangeResponse.Envelope> list(
            @RequestParam(value = "group", required = false) String groupName,
            @RequestParam(value = "teacher", required = false) String teacherLabel,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        log.info("GET Запрос: /api/v1/changes?group={}&teacher={}&from={}&to={}", groupName, teacherLabel, from, to);

        Long groupMasterId = groupName == null ? null : masterDirectoryService.groupId(groupName);
        Long teacherMasterId = teacherLabel == null ? null : masterDirectoryService.teacherId(teacherLabel);

        // спросили про запись, которой нет в справочнике: изменений у неё быть не может, но
        // это не ошибка запроса — та же логика, что у выдачи расписания
        if (groupName != null && groupMasterId == null) {
            log.info("Группа {} не найдена в справочнике — изменений нет", groupName);
            return ResponseEntity.ok(changeMapper.toEnvelope(List.of()));
        }
        if (teacherLabel != null && teacherMasterId == null) {
            log.info("Преподаватель {} не найден в справочнике — изменений нет", teacherLabel);
            return ResponseEntity.ok(changeMapper.toEnvelope(List.of()));
        }

        return ResponseEntity.ok(changeMapper.toEnvelope(
                changeService.list(groupMasterId, teacherMasterId, from, to)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ROLE_SCHEDULE')")
    public ResponseEntity<ChangeResponse> create(
            @RequestBody ChangeRequest request,
            @RequestHeader(value = AdminAccess.HEADER, required = false) String adminToken
    ) {
        log.info("POST Запрос: /api/v1/changes \n Body:\n {}", request);
        adminAccess.require(adminToken, "создание изменения");
        return ResponseEntity.ok(changeMapper.toResponse(changeService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_SCHEDULE')")
    public ResponseEntity<ChangeResponse> update(
            @PathVariable("id") Long id,
            @RequestBody ChangeRequest request,
            @RequestHeader(value = AdminAccess.HEADER, required = false) String adminToken
    ) {
        log.info("PUT Запрос: /api/v1/changes/{}", id);
        adminAccess.require(adminToken, "правка изменения " + id);
        return ResponseEntity.ok(changeMapper.toResponse(changeService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_SCHEDULE')")
    public ResponseEntity<String> delete(
            @PathVariable("id") Long id,
            @RequestHeader(value = AdminAccess.HEADER, required = false) String adminToken
    ) {
        log.info("DELETE Запрос: /api/v1/changes/{}", id);
        adminAccess.require(adminToken, "удаление изменения " + id);

        changeService.delete(id);
        return ResponseEntity.ok("Изменение удалено");
    }

}
