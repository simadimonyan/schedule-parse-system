package app.repository.models.dto.master;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/** Предмет в представлении мастер-сервиса. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MasterSubjectView(
        Long id,
        String name,

        @JsonProperty("subject_type")
        String subjectType,

        @JsonProperty("is_deleted")
        Boolean isDeleted
) implements Serializable {}
