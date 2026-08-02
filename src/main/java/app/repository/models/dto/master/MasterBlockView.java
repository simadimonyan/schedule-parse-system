package app.repository.models.dto.master;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/** Корпус в представлении мастер-сервиса. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MasterBlockView(
        Long id,
        String name,
        String address,
        String note,

        @JsonProperty("is_deleted")
        Boolean isDeleted
) implements Serializable {}
