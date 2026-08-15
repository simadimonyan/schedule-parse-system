package app.controller.api;

import app.repository.models.dto.api.parse.ParseResponse;
import app.security.AdminAccess;
import app.service.domain.persistence.SchedulePersistenceService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * Ручной запуск разбора файла расписания.
 *
 * <p>Обычно разбор запускает сам MinIO: загрузка файла в бакет шлёт вебхук. Эта ручка нужна,
 * когда вебхук не сработал или файл лежит в бакете давно — перезалить его ради повторного
 * разбора не хочется.
 *
 * <p>Имя файла необязательно: не назвали — берётся последний загруженный. Разбор идёт в
 * фоне, поэтому ответ говорит лишь о том, какой файл принят в работу; результат смотрят в
 * логе и в самом расписании.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/schedule/schedule")
@SecurityRequirement(name = "Authorization")
public class ParseController {

    private final SchedulePersistenceService persistenceService;
    private final AdminAccess adminAccess;

    public ParseController(SchedulePersistenceService persistenceService, AdminAccess adminAccess) {
        this.persistenceService = persistenceService;
        this.adminAccess = adminAccess;
    }

    @PostMapping("/parse")
    @PreAuthorize("hasRole('ROLE_SCHEDULE')")
    public ResponseEntity<ParseResponse> parse(
            @RequestParam(value = "file", required = false) String fileName,
            @RequestHeader(value = AdminAccess.HEADER, required = false) String adminToken
    ) throws IOException {
        log.info("POST Запрос: /api/v1/schedule/parse?file={}", fileName);
        adminAccess.require(adminToken, "разбор файла расписания");

        String resolved = persistenceService.resolveFile(fileName);
        if (resolved == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ParseResponse(
                    null,
                    fileName == null || fileName.isBlank()
                            ? "Бакет расписания пуст — разбирать нечего"
                            : "Файла нет в бакете расписания: " + fileName));
        }

        persistenceService.persistSchedule(resolved);
        return ResponseEntity.accepted().body(new ParseResponse(resolved, "Файл принят в разбор"));
    }

}
