package app.service.domain.persistence;

import app.repository.dao.ChangeRepository;
import app.repository.dao.ScheduleRepository;
import app.repository.models.dto.event.ScheduleEvent;
import app.repository.models.entity.Schedule;
import app.repository.models.entity.Version;
import app.service.domain.version.VersionService;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Запись разобранного расписания в базу.
 *
 * <p>Отдельный бин ради транзакции: обмен со справочником мастер-сервиса идёт до вставки —
 * пара ссылается на группу идентификатором, и без него её негде записать, — а держать
 * соединение с базой открытым на время сетевых вызовов нельзя. Если бы этот метод остался в
 * {@link SchedulePersistenceService}, вызов был бы внутренним, прошёл бы мимо прокси, и ни
 * транзакции, ни сброса кеша не случилось бы вовсе.
 *
 * <p>Пишет в версию, открытую на запись: пока черновика нет — в активную, как работало до
 * появления версий. Открытый черновик перехватывает загрузки на себя, и клиенты до
 * публикации остаются на прежнем расписании.
 */
@Slf4j
@Service
public class ScheduleWriter {

    private final ScheduleRepository scheduleRepository;
    private final ChangeRepository changeRepository;
    private final VersionService versionService;
    private final SlotResolver slotResolver;
    private final ApplicationEventPublisher eventPublisher;

    public ScheduleWriter(
            ScheduleRepository scheduleRepository,
            ChangeRepository changeRepository,
            VersionService versionService,
            SlotResolver slotResolver,
            ApplicationEventPublisher eventPublisher
    ) {
        this.scheduleRepository = scheduleRepository;
        this.changeRepository = changeRepository;
        this.versionService = versionService;
        this.slotResolver = slotResolver;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Заменяет расписание переданных групп целиком.
     *
     * <p>Файл — источник правды по своим группам: пары, которых в нём больше нет, должны
     * исчезнуть и из базы, поэтому старые строки сносятся, а не дополняются. Сносятся только
     * внутри той версии, куда идёт запись, — прочие снимки остаются целыми, ради этого версии
     * и заведены.
     *
     * <p>Сбрасываются и списки групп с преподавателями: они больше не лежат в своих
     * таблицах, а собираются из самих пар, и новая загрузка меняет их состав.
     */
    @Transactional
    @CacheEvict(value = {"schedule", "groups", "teachers", "levels", "courses"}, allEntries = true)
    public void replace(List<Schedule> schedules) {
        if (schedules.isEmpty()) return;

        Version target = versionService.writable();

        Set<Long> groupMasterIds = schedules.stream()
                .map(Schedule::getGroupMasterId)
                .collect(Collectors.toSet());

        for (Schedule schedule : schedules) {
            schedule.setVersion(target);
            schedule.setIsDeleted(false);
        }
        slotResolver.resolve(target, schedules);

        // изменения ссылаются на пары: не отвязав, старые строки не удалить — внешний ключ
        // не пропустит, и загрузка файла упала бы на первой же группе с переносом
        int detached = changeRepository.detachFromGroups(target.getId(), groupMasterIds);
        if (detached > 0) {
            log.info("Отвязано {} изменений от заменяемых пар: они остались в версии {} без привязки",
                    detached, target.getId());
        }

        scheduleRepository.deleteByVersionAndGroupMasterIdIn(target.getId(), groupMasterIds);
        scheduleRepository.saveAllAndFlush(schedules);

        log.info("В версию {} ({}) сохранено {} записей расписания для {} групп",
                target.getId(),
                Boolean.TRUE.equals(target.getIsDraft()) ? "черновик" : "активная",
                schedules.size(), groupMasterIds.size());

        // подписчикам сообщается только состоявшаяся запись: событие уходит после коммита
        // этого метода, а не отсюда — см. ScheduleEventProducer
        eventPublisher.publishEvent(ScheduleEvent.versionParsed(target.getId(), named(groupMasterIds)));
    }

    /**
     * Ставит пары по одной ячейке сетки, не трогая остальную неделю.
     *
     * <p>Это запись из редактора, а не из файла. Диспетчер приносит одну пару и ждёт, что
     * встанет ровно она; замена по группе, которой живёт {@link #replace}, стёрла бы всё
     * остальное расписание группы. Поэтому чистится только клетка «группа + слот», и только
     * та, в которую что-то кладут.
     *
     * <p>Пара приходит без идентификатора — у клиента его и нет, он показывает сетку, а не
     * список строк. Ячейка и есть ключ: повторная отправка той же клетки заменяет её
     * содержимое, а не кладёт рядом двойника.
     */
    @Transactional
    @CacheEvict(value = {"schedule", "groups", "teachers", "levels", "courses"}, allEntries = true)
    public void upsert(Version target, List<Schedule> schedules) {
        if (schedules.isEmpty()) return;

        for (Schedule schedule : schedules) {
            schedule.setVersion(target);
            schedule.setIsDeleted(false);
        }
        slotResolver.resolve(target, schedules);

        Set<List<Long>> cells = new LinkedHashSet<>();
        for (Schedule schedule : schedules) {
            // у только что заведённого слота прежних пар быть не может: клетку саму завела
            // эта же запись, чистить в ней нечего
            if (schedule.getSlot() == null || schedule.getSlot().getId() == null) continue;
            if (schedule.getGroupMasterId() == null) continue;
            cells.add(List.of(schedule.getSlot().getId(), schedule.getGroupMasterId()));
        }

        for (List<Long> cell : cells) {
            Long slotId = cell.getFirst();
            Long groupMasterId = cell.getLast();

            // изменения ссылаются на пару: не отвязав, старую строку не удалить — внешний
            // ключ не пропустит, и правка ячейки упала бы на первом же переносе
            changeRepository.detachFromCell(target.getId(), groupMasterId, slotId);
            scheduleRepository.deleteCell(target.getId(), groupMasterId, slotId);
        }

        scheduleRepository.saveAllAndFlush(schedules);

        log.info("В версию {} ({}) поставлено {} пар в {} ячеек сетки",
                target.getId(),
                Boolean.TRUE.equals(target.getIsDraft()) ? "черновик" : "активная",
                schedules.size(), cells.size());

        eventPublisher.publishEvent(ScheduleEvent.scheduleUpdated(
                target.getId(),
                named(schedules.stream().map(Schedule::getGroupMasterId).collect(Collectors.toSet()))));
    }

    /**
     * Группы события: без пустых и в устойчивом порядке.
     *
     * <p>Пара без группы сюда не доходит — её отсеивает разбор, — но {@code null} в списке
     * подписчика всё равно ждёт как идентификатор группы, и лучше его не отправлять вовсе.
     */
    private static List<Long> named(Set<Long> groupMasterIds) {
        return groupMasterIds.stream().filter(Objects::nonNull).sorted().toList();
    }

}
