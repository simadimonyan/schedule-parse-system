package app.service.domain.slot;

import app.repository.dao.TimeSlotRepository;
import app.repository.models.dto.api.slot.SlotRequest;
import app.repository.models.entity.TimeSlot;
import app.repository.models.entity.Version;
import app.service.domain.version.VersionService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Сетка недели: правка слотов руками.
 *
 * <p>Обычно слоты заводит разбор файла — {@code SlotResolver} собирает их из самих пар.
 * Этот сервис нужен для двух других случаев: задать сетку заранее, до первой загрузки, и
 * поправить время звонков, не перезаливая расписание.
 *
 * <p>Читается активная версия, пишется та, что открыта на запись: пока черновика нет — та же
 * активная, иначе черновик. Слот принадлежит версии, и править сетку опубликованного
 * расписания через черновик — ровно то, ради чего черновик открывают.
 */
@Slf4j
@Service
public class SlotService {

    private final TimeSlotRepository timeSlotRepository;
    private final VersionService versionService;

    public SlotService(TimeSlotRepository timeSlotRepository, VersionService versionService) {
        this.timeSlotRepository = timeSlotRepository;
        this.versionService = versionService;
    }

    /** Сетка активной версии или указанной, если версию назвали явно. */
    public List<TimeSlot> list(Long versionId) {
        return timeSlotRepository.findAllByVersion(versionId == null ? versionService.activeId() : versionId);
    }

    /**
     * Заводит слот или дополняет совпавший по естественному ключу.
     *
     * <p>Совпадением считается день, чётность, номер пары и строка времени внутри версии —
     * тот же ключ, по которому слоты подбирает разбор файла. Двух таких строк быть не должно:
     * это одна и та же ячейка сетки, у которой просто уточнили границы.
     */
    @Transactional
    public TimeSlot create(SlotRequest request) {
        validate(request);

        Version version = versionService.writable();
        String timeRange = request.timeRange() == null ? "" : request.timeRange().trim();

        TimeSlot slot = timeSlotRepository
                .findByVersionIdAndDayWeekAndWeekCountAndLessonCountAndTimeRange(
                        version.getId(), request.dayWeek(), request.weekCount(), request.lessonCount(), timeRange)
                .orElseGet(() -> {
                    TimeSlot savable = new TimeSlot();
                    savable.setVersion(version);
                    savable.setDayWeek(request.dayWeek());
                    savable.setWeekCount(request.weekCount());
                    savable.setLessonCount(request.lessonCount());
                    savable.setTimeRange(timeRange);
                    return savable;
                });

        slot.setStartedAt(request.startedAt());
        slot.setFinishedAt(request.finishedAt());
        slot.setIsDeleted(false);

        TimeSlot saved = timeSlotRepository.save(slot);
        log.info("Слот {} {} пара {} ({}) сохранён в версии {}",
                saved.getDayWeek(), saved.getWeekCount(), saved.getLessonCount(),
                saved.getTimeRange(), version.getId());
        return saved;
    }

    @Transactional
    public TimeSlot update(Long slotId, SlotRequest request) {
        validate(request);

        TimeSlot slot = timeSlotRepository.findById(slotId)
                .orElseThrow(() -> new EntityNotFoundException("Слот " + slotId + " не найден"));

        slot.setDayWeek(request.dayWeek());
        slot.setWeekCount(request.weekCount());
        slot.setLessonCount(request.lessonCount());
        slot.setTimeRange(request.timeRange() == null ? "" : request.timeRange().trim());
        slot.setStartedAt(request.startedAt());
        slot.setFinishedAt(request.finishedAt());

        return timeSlotRepository.save(slot);
    }

    /**
     * Мягкое удаление: строка остаётся, из сетки уходит.
     *
     * <p>Снести её насовсем нельзя — на слот ссылаются пары и изменения, и внешний ключ
     * удаление не пропустит. Да и не должен: пара, потерявшая своё место в сетке, — это
     * потерянная пара, а не освободившаяся строка.
     */
    @Transactional
    public void delete(Long slotId) {
        TimeSlot slot = timeSlotRepository.findById(slotId)
                .orElseThrow(() -> new EntityNotFoundException("Слот " + slotId + " не найден"));

        slot.setIsDeleted(true);
        timeSlotRepository.save(slot);
        log.info("Слот {} помечен удалённым", slotId);
    }

    private static void validate(SlotRequest request) {
        if (request.dayWeek() == null || request.dayWeek().isBlank()) {
            throw new IllegalArgumentException("День недели обязателен");
        }
        if (request.lessonCount() == null) {
            throw new IllegalArgumentException("Номер пары обязателен");
        }
        if (request.weekCount() == null) {
            throw new IllegalArgumentException("Чётность недели обязательна");
        }
        if (request.startedAt() != null && request.finishedAt() != null
                && request.finishedAt().isBefore(request.startedAt())) {
            throw new IllegalArgumentException(
                    "Конец пары раньше начала: " + request.startedAt() + " — " + request.finishedAt());
        }
    }

}
