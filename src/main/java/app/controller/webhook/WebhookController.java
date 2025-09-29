package app.controller.webhook;

import app.repository.models.dto.minio.MinioEvent;
import app.service.cache.CacheService;
import app.service.persistence.PersistenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/minio-webhook")
public class WebhookController {

    private final PersistenceService persistenceService;
    private final CacheService cacheService;


    @Autowired
    public WebhookController(PersistenceService persistenceService, CacheService cacheService) {
        this.persistenceService = persistenceService;
        this.cacheService = cacheService;
    }

    @PostMapping
    public ResponseEntity<String> handleEvent(@RequestBody String body) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            MinioEvent event = mapper.readValue(body, MinioEvent.class);
            log.info("POST Запрос: /minio-webhook | Файл: {}", event.getKey());

            if (event.getKey().contains(".xls") || event.getKey().contains(".xlsx")) {
                cacheService.clearAllCaches();
                persistenceService.persistSchedule(event.getKey());
            }
        }
        catch (Exception e) {
            log.error(e.toString());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
        return ResponseEntity.ok("OK");
    }

}
