package app.controller.api;

import app.controller.exceptions.ServiceException;
import app.repository.dao.ConfigRepository;
import app.repository.models.dto.api.configuration.WeekResponse;
import app.repository.models.entity.Config;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/configuration")
@SecurityRequirement(name = "Authorization")
public class ConfigController {

    private final ConfigRepository configRepository;

    public ConfigController(ConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    @GetMapping("/week")
    public ResponseEntity<WeekResponse> week() throws EntityNotFoundException {
        Config pair = configRepository.findAllByKey("weekCount").orElse(null);
        if (pair == null) throw new EntityNotFoundException("База не содержит четность недели");
        return ResponseEntity.ok(new WeekResponse(Integer.parseInt(pair.getValue())));
    }

}
