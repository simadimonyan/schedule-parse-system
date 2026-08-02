package app.repository.models.dto.master;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.List;

/** Тело создания и обновления преподавателей в мастер-сервисе. */
public record MasterTeacherRequest(
        List<TeacherItem> items
) implements Serializable {

    /**
     * Расписание знает преподавателя одной строкой «Фамилия И.О.», мастер хранит ФИО по
     * частям — строка разбирается при публикации ({@code MasterSyncService}).
     *
     * <p>{@code label} у мастера — должность или звание, а не отображаемое имя, поэтому
     * исходная строка расписания туда не пишется.
     */
    public record TeacherItem(
            Long id,
            String label,
            String name,

            @JsonProperty("last_name")
            String lastName,

            String patronymic,
            String status
    ) implements Serializable {}

}
