package app.service.domain.persistence;

import app.repository.dao.ChangeRepository;
import app.repository.dao.ScheduleRepository;
import app.repository.models.entity.Schedule;
import app.repository.models.entity.Version;
import app.service.domain.version.VersionService;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.util.List;
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

    public ScheduleWriter(
            ScheduleRepository scheduleRepository,
            ChangeRepository changeRepository,
            VersionService versionService,
            SlotResolver slotResolver
    ) {
        this.scheduleRepository = scheduleRepository;
        this.changeRepository = changeRepository;
        this.versionService = versionService;
        this.slotResolver = slotResolver;
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
    }

}
