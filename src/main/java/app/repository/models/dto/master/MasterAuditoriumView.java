package app.repository.models.dto.master;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/** Аудитория в представлении мастер-сервиса. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MasterAuditoriumView(
        Long id,
        BlockView block,
        String type,
        Integer floor,
        String number,
        Integer capacity,

        @JsonProperty("is_deleted")
        Boolean isDeleted
) implements Serializable {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BlockView(
            Long id,
            String name
    ) implements Serializable {}

}
