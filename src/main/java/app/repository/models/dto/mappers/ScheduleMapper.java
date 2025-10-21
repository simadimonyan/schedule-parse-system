package app.repository.models.dto.mappers;

import app.repository.models.dto.api.schedule.ScheduleResponse;
import app.repository.models.entity.Schedule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ScheduleMapper {

    private final GroupMapper groupMapper;
    private final TeacherMapper teacherMapper;

    @Autowired
    public ScheduleMapper(GroupMapper groupMapper, TeacherMapper teacherMapper) {
        this.groupMapper = groupMapper;
        this.teacherMapper = teacherMapper;
    }

    public ScheduleResponse toScheduleResponse(List<Schedule> schedule) {
        List<ScheduleResponse.ScheduleUnit> units = new ArrayList<>();
        for (Schedule s : schedule) {
            units.add(new ScheduleResponse.ScheduleUnit(
                    s.getId(),
                    s.getDayWeek(),
                    s.getTimePeriod(),
                    s.getWeekCount(),
                    groupMapper.toGroupResponse(s.getGroup()),
                    s.getLessonCount(),
                    s.getLessonType(),
                    s.getLessonName(),
                    s.getTeacher() != null ? teacherMapper.toTeacherResponse(s.getTeacher()) : null,
                    s.getAuditory(),
                    s.getEiosLink()
            ));
        }
        return new ScheduleResponse(units);
    }

}
