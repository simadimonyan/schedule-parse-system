package app.controller.api;

import app.repository.models.dto.api.configuration.OnlineEvent;
import app.repository.models.dto.api.configuration.WeekResponse;
import app.service.metrics.OnlineService;
import app.service.metrics.StatService;
import app.service.domain.persistence.SchedulePersistenceService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/v1/schedule/configuration")
@SecurityRequirement(name = "Authorization")
public class ConfigController {

    /** Шарим один пул на все SSE-соединения, чтобы каждый клиент не плодил
     *  новый поток. Daemon-флаг — чтобы JVM могла нормально завершиться. */
    private static final ScheduledExecutorService SSE_SCHEDULER =
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "sse-online-tick");
                t.setDaemon(true);
                return t;
            });

    private static final long SSE_TICK_SECONDS = 30L;

    @Value("${schedule.admin.token}")
    private String adminToken;

    private final SchedulePersistenceService persistenceService;
    private final OnlineService onlineService;
    private final StatService statService;

  public ConfigController(SchedulePersistenceService persistenceService, OnlineService onlineService, StatService statService) {
        this.persistenceService = persistenceService;
        this.onlineService = onlineService;
        this.statService = statService;
  }

    @GetMapping("/week")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<WeekResponse> week() throws EntityNotFoundException {
        log.info("GET Запрос: /api/v1/configuration/week");
        return ResponseEntity.ok(new WeekResponse(Integer.parseInt(persistenceService.getConfig("weekCount").getValue())));
    }

    @PostMapping("/week/swap")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<?> swapWeek(@RequestBody String token) throws EntityNotFoundException {
        log.info("POST Запрос: /api/v1/configuration/week/swap");
        if (token.startsWith("Bearer") && token.substring(7).equals(adminToken)) {
            persistenceService.swapWeek();
            return ResponseEntity.ok(new WeekResponse(Integer.parseInt(persistenceService.getConfig("weekCount").getValue())));
        }
        log.warn("Попытка доступа к административным функциям - доступ отказан");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Доступ отказан");
    }

    @PostMapping("/online/heartbeat/{uuid}")
    @PreAuthorize("hasRoles('ROLE_STUDENT', 'ROLE_STUDENT', 'ROLE_SCHEDULE')")
    public ResponseEntity<?> heartbeat(@PathVariable("uuid") UUID uuid) {
        log.info("POST Запрос: /api/v1/configuration/online/heartbeat/%s".formatted(uuid));
        onlineService.heartbeat(uuid);
        return ResponseEntity.ok("Вы теперь онлайн!");
    }

    @GetMapping(value = "/online/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasRoles('ROLE_STUDENT', 'ROLE_STUDENT', 'ROLE_SCHEDULE')")
    public SseEmitter stream(HttpServletResponse response) {
        // X-Accel-Buffering=no выключает буферизацию в nginx — без этого
        // заголовка прокси копит весь body и отдаёт его клиенту только
        // при закрытии стрима. На клиенте это выглядит как "счётчик
        // обновляется только когда соединение порвалось".
        // Cache-Control: no-transform запрещает прокси-сжатие (gzip),
        // которое тоже ломает realtime SSE.
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("Connection", "keep-alive");

        // SseEmitter с timeout=0 — сервер не закрывает соединение по
        // таймауту; реконнектом управляет клиент (см. presence.ts).
        SseEmitter emitter = new SseEmitter(0L);

        Runnable tick = () -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("online-event")
                        .data(new OnlineEvent(onlineService.getOnline().size()),
                              MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException ex) {
                emitter.completeWithError(ex);
            }
        };

        ScheduledFuture<?> future = SSE_SCHEDULER.scheduleAtFixedRate(
                tick, 0L, SSE_TICK_SECONDS, TimeUnit.SECONDS);

        Runnable cancel = () -> future.cancel(false);
        emitter.onCompletion(cancel);
        emitter.onTimeout(() -> { cancel.run(); emitter.complete(); });
        emitter.onError(e -> cancel.run());

        return emitter;
    }

    @GetMapping("/online/top/{mode}")
    @PreAuthorize("hasRoles('ROLE_STUDENT', 'ROLE_STUDENT', 'ROLE_SCHEDULE')")
    public ResponseEntity<?> top(@PathVariable("mode") String mode) {
        log.info("GET Запрос: /api/v1/configuration/online/top?mode=%s".formatted(mode));
        return ResponseEntity.ok(mode.equals("groups") ? statService.getTopGroupsByViews() : statService.getTopTeachersByViews());
    }

}
