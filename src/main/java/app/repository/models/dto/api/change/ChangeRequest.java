package app.repository.models.dto.api.change;

import app.repository.models.entity.ChangeType;

import java.time.LocalDate;
import java.util.Map;

/**
 * Изменение расписания: перенос, отмена, замена или разовая пара.
 *
 * <p>Заполнять все поля не нужно и не следует. Пустое значение означает «не менялось»: у
 * отмены нет ни аудитории, ни замены, у переноса меняется только слот. Заполненные поля —
 * это то, чем пара стала, а не чем была.
 *
 * <p>{@code scheduleId} — пара, к которой относится изменение: именно по нему изменение
 * попадёт внутрь этой пары в выдаче расписания. Без него изменение уходит в выдачу отдельным
 * списком — так заводят разовое занятие, которого в расписании не было.
 *
 * <p>{@code payload} — свободные данные, которым не нашлось колонки: номер приказа,
 * комментарий деканата, исходные значения. Искать по ним нельзя, индексов на них нет.
 */
public record ChangeRequest(
        ChangeType changeType,
        LocalDate changeDate,
        Long scheduleId,
        Long slotId,
        Long subjectMasterId,
        Long teacherMasterId,
        Long groupMasterId,
        Long auditoriumMasterId,
        Long departmentMasterId,
        Map<String, Object> payload
) {}
