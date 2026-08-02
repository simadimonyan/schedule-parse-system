package app.service.domain.workschedule;

import app.repository.dao.WorkScheduleRepository;
import app.repository.models.dto.api.workschedule.WorkScheduleRequest;
import app.repository.models.entity.Version;
import app.repository.models.entity.WorkSchedule;
import app.service.domain.version.VersionService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * График занятости преподавателей: когда им можно ставить пары.
 *
 * <p>Ограничение для раскладки, а не расписание. Строки отвечают на вопрос «куда пару
 * поставить можно», сами пары лежат в {@code Schedule}. Пустой график означает «ограничений
 * нет», а не «преподаватель не работает никогда»: заполнять его на всех необязательно.
 */
@Slf4j
@Service
public class WorkScheduleService {

    private final WorkScheduleRepository workScheduleRepository;
    private final VersionService versionService;

    public WorkScheduleService(WorkScheduleRepository workScheduleRepository, VersionService versionService) {
        this.workScheduleRepository = workScheduleRepository;
        this.versionService = versionService;
    }

    /** График активной версии: одного преподавателя или всех сразу. */
    public List<WorkSchedule> list(Long teacherMasterId) {
        Long versionId = versionService.activeId();

        if (teacherMasterId != null) return workScheduleRepository.findAllByTeacher(versionId, teacherMasterId);

        return workScheduleRepository.findAllByVersion(versionId).stream()
                .filter(work -> !Boolean.TRUE.equals(work.getIsDeleted()))
                .toList();
    }

    @Transactional
    public WorkSchedule create(WorkScheduleRequest request) {
        validate(request);

        Version version = versionService.writable();

        WorkSchedule work = new WorkSchedule();
        work.setVersion(version);
        apply(work, request);

        WorkSchedule saved = workScheduleRepository.save(work);
        log.info("График занятости преподавателя {} на {} сохранён в версии {}",
                saved.getTeacherMasterId(), saved.getDayWeek(), version.getId());
        return saved;
    }

    @Transactional
    public WorkSchedule update(Long workScheduleId, WorkScheduleRequest request) {
        validate(request);

        WorkSchedule work = workScheduleRepository.findById(workScheduleId)
                .orElseThrow(() -> new EntityNotFoundException("График занятости " + workScheduleId + " не найден"));

        apply(work, request);
        return workScheduleRepository.save(work);
    }

    @Transactional
    public void delete(Long workScheduleId) {
        WorkSchedule work = workScheduleRepository.findById(workScheduleId)
                .orElseThrow(() -> new EntityNotFoundException("График занятости " + workScheduleId + " не найден"));

        work.setIsDeleted(true);
        workScheduleRepository.save(work);
        log.info("График занятости {} помечен удалённым", workScheduleId);
    }

    private static void apply(WorkSchedule work, WorkScheduleRequest request) {
        work.setTeacherMasterId(request.teacherMasterId());
        work.setDayWeek(request.dayWeek());
        work.setStartedAt(request.startedAt());
        work.setFinishedAt(request.finishedAt());
        work.setIsDeleted(false);
    }

    private static void validate(WorkScheduleRequest request) {
        if (request.teacherMasterId() == null) {
            throw new IllegalArgumentException("Преподаватель обязателен");
        }
        if (request.dayWeek() == null || request.dayWeek().isBlank()) {
            throw new IllegalArgumentException("День недели обязателен");
        }
        if (request.startedAt() == null || request.finishedAt() == null) {
            throw new IllegalArgumentException("Границы окна обязательны: график без времени ничего не ограничивает");
        }
        if (request.finishedAt().isBefore(request.startedAt())) {
            throw new IllegalArgumentException(
                    "Конец окна раньше начала: " + request.startedAt() + " — " + request.finishedAt());
        }
    }

}
