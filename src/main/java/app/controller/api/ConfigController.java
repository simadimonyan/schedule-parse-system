package app.controller.api;

import app.repository.models.dto.api.auth.AdminToken;
import app.repository.models.dto.api.configuration.WeekResponse;
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

    private final SchedulePersistenceService schedulePersistenceService;

    public ConfigController(SchedulePersistenceService schedulePersistenceService) {
        this.schedulePersistenceService = schedulePersistenceService;
    }

    @GetMapping("/week")
    public ResponseEntity<WeekResponse> week() throws EntityNotFoundException {
        log.info("GET Запрос: /api/v1/configuration/week");
        return ResponseEntity.ok(new WeekResponse(Integer.parseInt(schedulePersistenceService.getConfig("weekCount").getValue())));
    }

    @PostMapping("/week/swap")
    public ResponseEntity<?> swapWeek(@RequestBody AdminToken token) throws EntityNotFoundException {
        log.info("POST Запрос: /api/v1/configuration/week/swap");
        if (token.token().startsWith("Bearer") && token.token().substring(7).equals(adminToken)) {
            schedulePersistenceService.swapWeek();
            return ResponseEntity.ok(new WeekResponse(Integer.parseInt(schedulePersistenceService.getConfig("weekCount").getValue())));
        }
        log.warn("Попытка доступа к административным функциям - доступ отказан");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Доступ отказан");
    }

}
