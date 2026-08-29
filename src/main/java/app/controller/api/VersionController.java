package app.controller.api;

import app.repository.models.dto.api.version.VersionResponse;
import app.repository.models.dto.api.version.VersionsResponse;
import app.repository.models.entity.Version;
import app.service.domain.version.VersionService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Управление версиями расписания.
 *
 * <p>Чтение открыто всем, у кого есть токен сервиса: список версий — часть картины
 * расписания. Переключение, публикация и удаление требуют роли {@code schedule} (или
 * административного токена, который даёт её же): одна такая операция меняет то, что видят
 * все клиенты сразу.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/schedule/versions")
@SecurityRequirement(name = "Authorization")
public class VersionController {

    private final VersionService versionService;

    public VersionController(VersionService versionService) {
        this.versionService = versionService;
    }

    @GetMapping
    public ResponseEntity<VersionsResponse> list() {
        log.info("GET Запрос: /api/v1/versions");
        return ResponseEntity.ok(new VersionsResponse(
                versionService.list().stream().map(VersionController::toResponse).toList()));
    }

    @GetMapping("/active")
    public ResponseEntity<VersionResponse> active() {
        log.info("GET Запрос: /api/v1/versions/active");
        return ResponseEntity.ok(toResponse(versionService.active()));
    }

    /**
     * Открывает черновик — копию активной версии, в которую пойдёт разбор файлов.
     *
     * <p>Сам собой черновик не заводится: он начинается копией активной версии, и делай это
     * каждая загрузка, десять файлов за заход означали бы десять полных копий расписания.
     * Пока черновика нет, разбор пишет прямо в активную версию.
     */
    @PostMapping("/draft")
    @PreAuthorize("hasRole('ROLE_SCHEDULE')")
    public ResponseEntity<VersionResponse> draft(
    ) {
        log.info("POST Запрос: /api/v1/versions/draft");
        return ResponseEntity.ok(toResponse(versionService.draft()));
    }

    /** Делает версию активной. Тем же вызовом откатываются на старую. */
    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ROLE_SCHEDULE')")
    public ResponseEntity<VersionResponse> activate(
            @PathVariable("id") Long id
    ) {
        log.info("POST Запрос: /api/v1/versions/{}/activate", id);
        return ResponseEntity.ok(toResponse(versionService.activate(id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_SCHEDULE')")
    public ResponseEntity<String> discard(
            @PathVariable("id") Long id
    ) {
        log.info("DELETE Запрос: /api/v1/versions/{}", id);
        versionService.discard(id);
        return ResponseEntity.ok("Версия удалена");
    }

    private static VersionResponse toResponse(Version version) {
        return new VersionResponse(
                version.getId(),
                version.getName(),
                version.getIsActive(),
                version.getIsDraft(),
                version.getCreatedAt(),
                version.getUpdatedAt());
    }

}
