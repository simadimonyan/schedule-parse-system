package app.repository.models.dto.api.workschedule;

import java.time.LocalTime;

/**
 * Окно занятости преподавателя в дне недели.
 *
 * <p>Это ограничение для раскладки, а не пара: строка говорит, куда пару ставить можно.
 * Преподаватель задан идентификатором справочника мастера — на запись сервис работает
 * идентификаторами, а имена принимает только на чтение.
 */
public record WorkScheduleRequest(
        Long teacherMasterId,
        String dayWeek,
        LocalTime startedAt,
        LocalTime finishedAt
) {}
