package app.repository.models.dto.api.slot;

import java.time.LocalTime;

/**
 * Ячейка недельной сетки, заводимая руками.
 *
 * <p>Слоты обычно появляются сами при разборе файла — из дня, чётности, номера пары и
 * времени. Руками их правят, когда сетку задают заранее, до первой загрузки, или когда
 * время звонков поменялось, а расписание перезаливать не нужно.
 *
 * <p>{@code timeRange} — строка вида «8:00-9:30», часть естественного ключа слота: именно в
 * таком виде время приходит из файла, и подменять его разобранными границами нельзя, иначе
 * разбор перестанет узнавать уже заведённые слоты.
 */
public record SlotRequest(
        String dayWeek,
        Integer weekCount,
        Integer lessonCount,
        String timeRange,
        LocalTime startedAt,
        LocalTime finishedAt
) {}
