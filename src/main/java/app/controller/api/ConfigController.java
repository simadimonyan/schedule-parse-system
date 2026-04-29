package app.controller.api;

import app.repository.models.dto.api.configuration.OnlineEvent;
import app.repository.models.dto.api.configuration.WeekResponse;
import app.service.max.MaxService;
import app.service.metrics.OnlineService;
import app.service.metrics.StatService;
import app.service.persistence.SchedulePersistenceService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/v1/configuration")
@SecurityRequirement(name = "Authorization")
public class ConfigController {

    @Value("${schedule.admin.token}")
    private String adminToken;

    private final SchedulePersistenceService persistenceService;
    private final OnlineService onlineService;
    private final StatService statService;
    private final MaxService maxService;

    public ConfigController(SchedulePersistenceService persistenceService, OnlineService onlineService, StatService statService, MaxService maxService) {
        this.persistenceService = persistenceService;
        this.onlineService = onlineService;
        this.statService = statService;
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

    @PostMapping("/schedule/load/{group}")
    public ResponseEntity<?> loadExternalSchedule(@PathVariable("group") String groupName, @RequestBody String token) {
        log.info("POST Запрос: /api/v1/configuration/schedule/load");
        if (token.startsWith("Bearer") && token.substring(7).equals(adminToken)) {
            maxService.loadAndPersistSchedule(groupName);
            return ResponseEntity.ok("Процесс запущен!");
        }
        log.warn("Попытка доступа к административным функциям - доступ отказан");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Доступ отказан");
    }

    @PostMapping("/online/heartbeat/{uuid}")
    public ResponseEntity<?> heartbeat(@PathVariable("uuid") UUID uuid) {
        log.info("POST Запрос: /api/v1/configuration/online/heartbeat/%s".formatted(uuid));
        onlineService.heartbeat(uuid);
        return ResponseEntity.ok("Вы теперь онлайн!");
    }

    @GetMapping(value = "/online/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<OnlineEvent>> stream() {
        return Flux.interval(Duration.ofSeconds(30))
                .map(i -> ServerSentEvent.builder(
                             new OnlineEvent(onlineService.getOnline().size())
                        )
                        .event("online-event")
                        .build());
    }

    @GetMapping("/online/top/{mode}")
    public ResponseEntity<?> top(@PathVariable("mode") String mode) {
        log.info("GET Запрос: /api/v1/configuration/online/top?mode=%s".formatted(mode));
        return ResponseEntity.ok(mode.equals("groups") ? statService.getTopGroupsByViews() : statService.getTopTeachersByViews());
    }

}
