package app.repository.models.dto.api.schedule;

import app.repository.models.dto.api.change.ChangeResponse;
import app.repository.models.dto.api.group.GroupResponse;
import app.repository.models.dto.api.teacher.TeacherResponse;

import java.util.List;

/**
 * Расписание в ответе API.
 *
 * <p>Изменения приезжают вместе с парами, а не отдельной ручкой, и ничего из выдачи не
 * убирают: отменённая пара приходит как обычно, но со своей отменой внутри — рисует её
 * клиент. Решать за него, показывать пару или нет, сервер не должен: в сетке на её месте
 * иначе окажется дыра без объяснения.
 *
 * @param schedule пары со своими изменениями
 * @param changes  изменения, которым пары нет: разовые занятия, которых не было в файле, и
 *                 общие распоряжения по группе
 */
public record ScheduleResponse(
        List<ScheduleUnit> schedule,
        List<ChangeResponse> changes
) {

    public record ScheduleUnit(
            Long id,
            String dayWeek,
            String timePeriod,
            Integer weekCount,
            GroupResponse group,
            Integer lessonCount,
            String lessonType,
            String lessonName,
            String pinnedDate,
            TeacherResponse teacher,
            String auditory,
            String eiosLink,
            List<ChangeResponse> changes
    ) {}

}
