package app.repository.models.dto.mappers;

import app.repository.models.dto.api.schedule.ScheduleResponse;
import app.repository.models.entity.Change;
import app.repository.models.entity.Schedule;
import app.service.domain.persistence.ScheduleView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ScheduleMapper {

    private final GroupMapper groupMapper;
    private final TeacherMapper teacherMapper;
    private final ChangeMapper changeMapper;

    @Autowired
    public ScheduleMapper(GroupMapper groupMapper, TeacherMapper teacherMapper, ChangeMapper changeMapper) {
        this.groupMapper = groupMapper;
        this.teacherMapper = teacherMapper;
        this.changeMapper = changeMapper;
    }

    /** Расписание вместе с изменениями, которым не нашлось пары. */
    public ScheduleResponse toScheduleResponse(ScheduleView view) {
        return toScheduleResponse(view.schedule(), view.standaloneChanges());
    }

    public ScheduleResponse toScheduleResponse(List<Schedule> schedule) {
        return toScheduleResponse(schedule, List.of());
    }

    /**
     * @param standalone изменения без пары — разовые занятия и общие распоряжения; места в
     *                   сетке им нет, но показать их клиенту надо
     */
    public ScheduleResponse toScheduleResponse(List<Schedule> schedule, List<Change> standalone) {
        List<ScheduleResponse.ScheduleUnit> units = new ArrayList<>();
        for (Schedule s : schedule) {
            units.add(new ScheduleResponse.ScheduleUnit(
                    s.getId(),
                    s.getDayWeek(),
                    s.getTimePeriod(),
                    s.getWeekCount(),
                    // группа могла пропасть из справочника после разбора файла — пара
                    // остаётся в выдаче, потому что занятие от этого не отменяется
                    s.getGroup() != null ? groupMapper.toGroupResponse(s.getGroup()) : null,
                    s.getLessonCount(),
                    s.getLessonType(),
                    s.getLessonName(),
                    s.getPinnedDate(),
                    s.getTeacher() != null ? teacherMapper.toTeacherResponse(s.getTeacher()) : null,
                    s.getAuditory(),
                    s.getEiosLink(),
                    // пустой список, а не null: клиенту не придётся проверять поле на
                    // существование перед тем, как по нему пройтись
                    s.getChanges() == null
                            ? List.of()
                            : s.getChanges().stream().map(changeMapper::toResponse).toList()
            ));
        }

        return new ScheduleResponse(
                units,
                standalone == null ? List.of() : standalone.stream().map(changeMapper::toResponse).toList());
    }

}
