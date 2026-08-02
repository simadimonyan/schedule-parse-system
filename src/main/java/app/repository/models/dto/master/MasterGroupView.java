package app.repository.models.dto.master;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/** Группа в представлении мастер-сервиса. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MasterGroupView(
        Long id,
        FacultyView faculty,
        Integer course,
        String name,
        String level,

        @JsonProperty("study_form")
        String studyForm,

        Integer capacity,

        @JsonProperty("is_deleted")
        Boolean isDeleted
) implements Serializable {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FacultyView(
            Long id,
            String name
    ) implements Serializable {}

}
