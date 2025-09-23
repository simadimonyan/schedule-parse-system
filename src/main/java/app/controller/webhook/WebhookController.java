package app.controller.webhook;

import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/minio-webhook")
public class WebhookController {

    private final MinioClient minioClient;

    @Autowired
    public WebhookController(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @PostMapping
    public ResponseEntity<String> handleEvent(@RequestBody String body) {
        try {
            log.info(body);
        }
        catch (Exception e) {
            log.error(e.toString());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
        return ResponseEntity.ok("OK");
    }

}
