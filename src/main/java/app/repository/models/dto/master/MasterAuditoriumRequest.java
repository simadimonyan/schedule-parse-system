package app.repository.models.dto.master;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.List;

/** Тело создания аудиторий в мастер-сервисе. */
public record MasterAuditoriumRequest(
        List<AuditoriumItem> items
) implements Serializable {

    /**
     * Из расписания известны только корпус и номер: «1-114а» разбирается на корпус «1» и
     * номер «114а». Если префикса нет («114а»), корпус неизвестен и подставляется корпус-
     * заглушка мастера. Этаж, вместимость и тип аудитории расписание не содержит.
     */
    public record AuditoriumItem(
            Long id,

            @JsonProperty("block_id")
            Long blockId,

            @JsonProperty("pinned_teacher_id")
            Long pinnedTeacherId,

            String type,
            Integer floor,
            String number,
            Integer capacity
    ) implements Serializable {}

}
