package app.service.domain.change;

import app.repository.dao.ChangeRepository;
import app.repository.dao.ScheduleRepository;
import app.repository.dao.TimeSlotRepository;
import app.repository.models.dto.api.change.ChangeRequest;
import app.repository.models.entity.Change;
import app.repository.models.entity.Schedule;
import app.repository.models.entity.TimeSlot;
import app.repository.models.entity.Version;
import app.service.domain.version.VersionService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Изменения расписания: переносы, отмены, замены и разовые пары.
 *
 * <p>Изменение — запись поверх расписания, а не правка пары. Пара остаётся на месте, а рядом
 * появляется «перенесена на 14 марта» или «отменена». Так видно и как было, и как стало;
 * правь мы саму пару, от исходника не осталось бы ничего, и отменить отмену было бы нечем.
 *
 * <p>Изменения всегда точечные: у каждого своя дата. Повторяющееся «каждый вторник» — это уже
 * не изменение, а другое расписание, и его место в новой версии.
 */
@Slf4j
@Service
public class ChangeService {

    private final ChangeRepository changeRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final ScheduleRepository scheduleRepository;
    private final VersionService versionService;

    public ChangeService(
            ChangeRepository changeRepository,
            TimeSlotRepository timeSlotRepository,
            ScheduleRepository scheduleRepository,
            VersionService versionService
    ) {
        this.changeRepository = changeRepository;
        this.timeSlotRepository = timeSlotRepository;
        this.scheduleRepository = scheduleRepository;
        this.versionService = versionService;
    }

    /**
     * Изменения активной версии с отбором по группе, преподавателю или отрезку дат.
     *
     * <p>Отборы не складываются: спрашивают либо «что у этой группы», либо «что у этого
     * преподавателя», либо «что на этой неделе». Комбинация «группа и даты» здесь не нужна и
     * стоила бы отдельного запроса с необязательными параметрами, которые Postgres
     * оптимизирует хуже трёх простых.
     */
    public List<Change> list(Long groupMasterId, Long teacherMasterId, LocalDate from, LocalDate to) {
        Long versionId = versionService.activeId();

        if (groupMasterId != null) return changeRepository.findAllByGroup(versionId, groupMasterId);
        if (teacherMasterId != null) return changeRepository.findAllByTeacher(versionId, teacherMasterId);
        if (from != null && to != null) return changeRepository.findAllByPeriod(versionId, from, to);

        return changeRepository.findAllByVersion(versionId).stream()
                .filter(change -> !Boolean.TRUE.equals(change.getIsDeleted()))
                .sorted(Comparator.comparing(
                        Change::getChangeDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    @Transactional
    public Change create(ChangeRequest request) {
        validate(request);

        Version version = versionService.writable();

        Change change = new Change();
        change.setVersion(version);
        change.setSlot(slot(request.slotId(), version));
        change.setSchedule(schedule(request.scheduleId(), version));
        apply(change, request);

        Change saved = changeRepository.save(change);
        log.info("Изменение {} на {} сохранено в версии {}",
                saved.getChangeType(), saved.getChangeDate(), version.getId());
        return saved;
    }

    @Transactional
    public Change update(Long changeId, ChangeRequest request) {
        validate(request);

        Change change = changeRepository.findById(changeId)
                .orElseThrow(() -> new EntityNotFoundException("Изменение " + changeId + " не найдено"));

        change.setSlot(slot(request.slotId(), change.getVersion()));
        change.setSchedule(schedule(request.scheduleId(), change.getVersion()));
        apply(change, request);

        return changeRepository.save(change);
    }

    /** Мягкое удаление: отменённая отмена возвращает паре её обычный вид. */
    @Transactional
    public void delete(Long changeId) {
        Change change = changeRepository.findById(changeId)
                .orElseThrow(() -> new EntityNotFoundException("Изменение " + changeId + " не найдено"));

        change.setIsDeleted(true);
        changeRepository.save(change);
        log.info("Изменение {} помечено удалённым", changeId);
    }

    private static void apply(Change change, ChangeRequest request) {
        change.setChangeType(request.changeType());
        change.setChangeDate(request.changeDate());
        change.setSubjectMasterId(request.subjectMasterId());
        change.setTeacherMasterId(request.teacherMasterId());
        change.setGroupMasterId(request.groupMasterId());
        change.setAuditoriumMasterId(request.auditoriumMasterId());
        change.setDepartmentMasterId(request.departmentMasterId());
        change.setPayload(request.payload());
        change.setIsDeleted(false);
    }

    /**
     * Слот изменения, если он назван.
     *
     * <p>Проверяется принадлежность версии: слот из чужого снимка означал бы изменение,
     * указывающее на ячейку сетки, которой в этой версии расписания нет.
     */
    private TimeSlot slot(Long slotId, Version version) {
        if (slotId == null) return null;

        TimeSlot slot = timeSlotRepository.findById(slotId)
                .orElseThrow(() -> new EntityNotFoundException("Слот " + slotId + " не найден"));

        Long slotVersionId = slot.getVersion() == null ? null : slot.getVersion().getId();
        if (!Objects.equals(slotVersionId, version.getId())) {
            throw new IllegalArgumentException(
                    "Слот " + slotId + " принадлежит версии " + slotVersionId + ", а изменение пишется в " + version.getId());
        }
        return slot;
    }

    /**
     * Пара, к которой цепляется изменение, если её назвали.
     *
     * <p>Проверяется принадлежность версии — по той же причине, что и у слота: изменение,
     * указывающее на пару из другого снимка расписания, не найдётся при выдаче ни там, ни там.
     */
    private Schedule schedule(Long scheduleId, Version version) {
        if (scheduleId == null) return null;

        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new EntityNotFoundException("Пара " + scheduleId + " не найдена"));

        Long scheduleVersionId = schedule.getVersion() == null ? null : schedule.getVersion().getId();
        if (!Objects.equals(scheduleVersionId, version.getId())) {
            throw new IllegalArgumentException(
                    "Пара " + scheduleId + " принадлежит версии " + scheduleVersionId + ", а изменение пишется в " + version.getId());
        }
        return schedule;
    }

    private static void validate(ChangeRequest request) {
        if (request.changeType() == null) {
            throw new IllegalArgumentException("Тип изменения обязателен");
        }
        if (request.changeDate() == null) {
            throw new IllegalArgumentException("Дата изменения обязательна: изменения точечные, а не еженедельные");
        }
        // без группы и преподавателя изменение некому показать: все запросы расписания идут
        // либо от группы, либо от преподавателя
        if (request.groupMasterId() == null && request.teacherMasterId() == null) {
            throw new IllegalArgumentException("Нужна хотя бы группа или преподаватель: иначе изменение не попадёт ни в одну выдачу");
        }
    }

}
