package app.controller.api;

import app.repository.models.dto.api.slot.SlotRequest;
import app.repository.models.dto.api.slot.SlotResponse;
import app.repository.models.dto.mappers.SlotMapper;
import app.security.AdminAccess;
import app.service.domain.slot.SlotService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.extern.slf4j.Slf4j;
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

/**
 * Сетка недели: номер пары, день, чётность, время звонков.
 *
 * <p>Чтение открыто всем с токеном сервиса — сетка нужна, чтобы нарисовать пустое
 * расписание. Правка требует административного токена в заголовке {@code X-Admin-Token}:
 * слоты общие на версию, и сдвиг звонков меняет расписание всем сразу.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/schedule/slots")
@SecurityRequirement(name = "Authorization")
public class SlotController {

    private final SlotService slotService;
    private final SlotMapper slotMapper;
    private final AdminAccess adminAccess;

    public SlotController(SlotService slotService, SlotMapper slotMapper, AdminAccess adminAccess) {
        this.slotService = slotService;
        this.slotMapper = slotMapper;
        this.adminAccess = adminAccess;
    }

    @GetMapping
    @PreAuthorize("hasRole('ROLE_SCHEDULE')")
    public ResponseEntity<SlotResponse.Envelope> list(
            @RequestParam(value = "versionId", required = false) Long versionId
    ) {
        log.info("GET Запрос: /api/v1/slots?versionId={}", versionId);
        return ResponseEntity.ok(slotMapper.toEnvelope(slotService.list(versionId)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ROLE_SCHEDULE')")
    public ResponseEntity<SlotResponse> create(
            @RequestBody SlotRequest request,
            @RequestHeader(value = AdminAccess.HEADER, required = false) String adminToken
    ) {
        log.info("POST Запрос: /api/v1/slots \n Body:\n {}", request);
        adminAccess.require(adminToken, "создание слота");
        return ResponseEntity.ok(slotMapper.toResponse(slotService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_SCHEDULE')")
    public ResponseEntity<SlotResponse> update(
            @PathVariable("id") Long id,
            @RequestBody SlotRequest request,
            @RequestHeader(value = AdminAccess.HEADER, required = false) String adminToken
    ) {
        log.info("PUT Запрос: /api/v1/slots/{}", id);
        adminAccess.require(adminToken, "правка слота " + id);
        return ResponseEntity.ok(slotMapper.toResponse(slotService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_SCHEDULE')")
    public ResponseEntity<String> delete(
            @PathVariable("id") Long id,
            @RequestHeader(value = AdminAccess.HEADER, required = false) String adminToken
    ) {
        log.info("DELETE Запрос: /api/v1/slots/{}", id);
        adminAccess.require(adminToken, "удаление слота " + id);

        slotService.delete(id);
        return ResponseEntity.ok("Слот удалён");
    }

}
