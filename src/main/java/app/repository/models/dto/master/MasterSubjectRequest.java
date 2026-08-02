package app.repository.models.dto.master;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.List;

/** Тело создания предметов в мастер-сервисе. */
public record MasterSubjectRequest(
        List<SubjectItem> items
) implements Serializable {

    /**
     * В расписании предмет — это только название ячейки. Тип занятия (лекция, практика,
     * лабораторная) относится к конкретной паре, а не к предмету: один и тот же предмет
     * читается и лекцией, и практикой. Поэтому {@code subjectType} не заполняется.
     */
    public record SubjectItem(
            Long id,
            String name,

            @JsonProperty("subject_type")
            String subjectType
    ) implements Serializable {}

}
