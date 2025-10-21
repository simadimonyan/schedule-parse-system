package app.repository.models.dto.api.schedule;

import app.repository.models.dto.api.group.GroupResponse;
import app.repository.models.dto.api.teacher.TeacherResponse;

import java.util.List;

public record ScheduleResponse(
        List<ScheduleUnit> schedule
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
            TeacherResponse teacher,
            String auditory,
            String eiosLink
    ) {}

}
