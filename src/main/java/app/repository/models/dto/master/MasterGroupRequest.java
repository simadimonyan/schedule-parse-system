package app.repository.models.dto.master;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.List;

/** Тело создания и обновления групп в мастер-сервисе. */
public record MasterGroupRequest(
        List<GroupItem> items
) implements Serializable {

    /**
     * {@code facultyId} у мастера обязателен, а в расписании факультета нет: подставляется
     * значение {@code master.default.faculty.id}.
     *
     * <p>{@code studyForm} — форма обучения (очная, заочная, очно-заочная): в расписании
     * она определяется по имени файла и передаётся в мастер.
     */
    public record GroupItem(
            Long id,

            @JsonProperty("faculty_id")
            Long facultyId,

            Integer course,
            String name,
            String level,

            @JsonProperty("study_form")
            String studyForm,

            Integer capacity
    ) implements Serializable {}

}
