package app.controller.api;

import app.repository.models.dto.api.configuration.WeekResponse;
import app.service.max.MaxService;
import app.service.persistence.SchedulePersistenceService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/configuration")
@SecurityRequirement(name = "Authorization")
public class ConfigController {

    @Value("${schedule.admin.token}")
    private String adminToken;

    private final SchedulePersistenceService persistenceService;
    private final MaxService maxService;

    public ConfigController(SchedulePersistenceService persistenceService, MaxService maxService) {
        this.persistenceService = persistenceService;
        this.maxService = maxService;
    }

    @GetMapping("/week")
    public ResponseEntity<WeekResponse> week() throws EntityNotFoundException {
        log.info("GET Запрос: /api/v1/configuration/week");
        return ResponseEntity.ok(new WeekResponse(Integer.parseInt(persistenceService.getConfig("weekCount").getValue())));
    }

    @PostMapping("/week/swap")
    public ResponseEntity<?> swapWeek(@RequestBody String token) throws EntityNotFoundException {
        log.info("POST Запрос: /api/v1/configuration/week/swap");
        if (token.startsWith("Bearer") && token.substring(7).equals(adminToken)) {
            persistenceService.swapWeek();
            return ResponseEntity.ok(new WeekResponse(Integer.parseInt(persistenceService.getConfig("weekCount").getValue())));
        }
        log.warn("Попытка доступа к административным функциям - доступ отказан");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Доступ отказан");
    }

    @PostMapping("/groups/load")
    public ResponseEntity<?> loadExternalGroups(@RequestBody String token) {
        log.info("POST Запрос: /api/v1/configuration/groups/load");
        if (token.startsWith("Bearer") && token.substring(7).equals(adminToken)) {
            maxService.loadAndPersistGroups();
            return ResponseEntity.ok("Процесс запущен!");
        }
        log.warn("Попытка доступа к административным функциям - доступ отказан");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Доступ отказан");
    }

    @PostMapping("/schedule/load")
    public ResponseEntity<?> loadExternalSchedule(@RequestBody String token) {
        log.info("POST Запрос: /api/v1/configuration/schedule/load");
        if (token.startsWith("Bearer") && token.substring(7).equals(adminToken)) {
            maxService.loadAndPersistSchedule();
            return ResponseEntity.ok("Процесс запущен!");
        }
        log.warn("Попытка доступа к административным функциям - доступ отказан");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Доступ отказан");
    }

}
