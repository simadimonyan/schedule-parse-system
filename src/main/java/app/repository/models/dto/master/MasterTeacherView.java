package app.repository.models.dto.master;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/** Преподаватель в представлении мастер-сервиса. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MasterTeacherView(
        Long id,
        String label,
        String name,

        @JsonProperty("last_name")
        String lastName,

        String patronymic,
        String status,

        @JsonProperty("is_deleted")
        Boolean isDeleted
) implements Serializable {}
