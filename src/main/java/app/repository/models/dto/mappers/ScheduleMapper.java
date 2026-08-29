package app.repository.models.dto.mappers;

import app.repository.models.dto.api.schedule.ScheduleBatch;
import app.repository.models.dto.api.schedule.ScheduleResponse;
import app.repository.models.entity.Change;
import app.repository.models.entity.Schedule;
import app.repository.models.entity.TimeSlot;
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
            // место в неделе живёт в слоте: у пары своих колонок под день, чётность, номер и
            // время больше нет. Слот приходит вместе с парой (join fetch), но выдача не
            // падает и без него — пара покажется без места, а не пропадёт с ошибкой
            TimeSlot slot = s.getSlot();

            units.add(new ScheduleResponse.ScheduleUnit(
                    s.getId(),
                    slot == null ? null : slot.getDayWeek(),
                    slot == null ? null : slot.getTimeRange(),
                    slot == null ? null : slot.getWeekCount(),
                    // группа могла пропасть из справочника после разбора файла — пара
                    // остаётся в выдаче, потому что занятие от этого не отменяется
                    s.getGroup() != null ? groupMapper.toGroupResponse(s.getGroup()) : null,
                    slot == null ? null : slot.getLessonCount(),
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

    /**
     * Пары из пачки клиента.
     *
     * <p>День, чётность, номер пары и время едут заготовкой слота: своих колонок под них у
     * пары нет, а готового слота может ещё не быть — сетку заводит уже сохранение, тем же
     * ключом, что и разбор файла.
     */
    public List<Schedule> toSchedule(List<ScheduleBatch.ScheduleBatchUnit> schedule) {
        ArrayList<Schedule> result = new ArrayList<>();

        for (ScheduleBatch.ScheduleBatchUnit item : schedule) {
            Schedule savable = new Schedule();
            savable.setGroup(item.group());
            savable.setGroupMasterId(item.groupMasterId());
            savable.setVersion(item.version());
            savable.setSlot(TimeSlot.draft(
                    item.dayWeek(), item.weekCount(), item.lessonCount(), item.timePeriod()));
            savable.setLessonType(item.lessonType());
            savable.setLessonName(item.lessonName());
            savable.setTeacher(item.teacher());
            savable.setTeacherMasterId(item.teacherMasterId());
            savable.setPinnedDate(item.pinnedDate());
            savable.setAuditory(item.auditory());
            savable.setEiosLink(item.eiosLink());
            result.add(savable);
        }

        return result;
    }

}
