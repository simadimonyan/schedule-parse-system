package app.service.domain.persistence;

import app.repository.models.entity.Change;
import app.repository.models.entity.Schedule;

import java.io.Serializable;
import java.util.List;

/**
 * Расписание вместе с изменениями, которым не нашлось пары.
 *
 * <p>Пара несёт свои изменения внутри себя ({@code Schedule.changes}), но не у всякого
 * изменения пара есть: разовое занятие, которого в файле не было, привязывать не к чему.
 * Потерять его нельзя — клиент как раз и должен дорисовать такое занятие в сетку, — поэтому
 * оно едет рядом, вторым списком.
 *
 * <p>{@link Serializable} обязателен: значение кладётся в кеш расписания в Redis.
 */
public record ScheduleView(
        List<Schedule> schedule,
        List<Change> standaloneChanges
) implements Serializable {

    public static ScheduleView empty() {
        return new ScheduleView(List.of(), List.of());
    }

}
