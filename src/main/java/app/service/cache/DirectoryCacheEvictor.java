package app.service.cache;

import app.repository.models.dto.directory.Group;
import app.repository.models.dto.directory.Teacher;
import app.repository.models.dto.master.kafka.CacheEvent;
import app.repository.models.dto.master.kafka.UnitType;
import app.service.infra.MasterDirectoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Сброс кеша по объявлению мастер-сервиса — ровно того, что от него могло устареть.
 *
 * <p>Раньше любое изменение справочника выносило кеш целиком. Заодно с ним улетало
 * расписание, которое стоит запроса к базе и похода в справочник за названиями, и конфиг
 * чётности недели, к справочнику отношения не имеющий. Один разбор нагрузки шлёт такое
 * объявление семь раз подряд, и расписание жило почти всегда холодным.
 *
 * <p>Правило по типу изменения:
 * <ul>
 *   <li>{@link UnitType#CREATE} — не трогаем ничего. Заведённой только что записи нет ни
 *       в одном отданном ответе: расписания у неё ещё нет, в выборки попадают лишь те, у
 *       кого оно есть, а неудачный поиск по имени не кешируется вовсе.</li>
 *   <li>{@link UnitType#UPDATE} — точечно ключ соответствия «имя → идентификатор» и
 *       целиком те регионы, ключ которых по одной записи не вычислить.</li>
 *   <li>{@link UnitType#DELETE} — то же, но имени удалённой записи взять уже неоткуда:
 *       справочник её не отдаёт. Соответствия сбрасываются целиком.</li>
 * </ul>
 *
 * <p>Переименование оставляет запись по старому имени: событие несёт идентификаторы, а
 * прежнее имя знал только кеш. Такой ключ доживёт до истечения TTL — час выдачи по имени,
 * которого больше нет, приемлемее, чем сканирование всех ключей на каждое объявление.
 */
@Slf4j
@Service
public class DirectoryCacheEvictor {

    // соответствия «имя → идентификатор справочника»: ключи 'group:<имя>' и 'teacher:<ФИО>'
    private static final String DIRECTORY = "directory";

    // выдача расписания несёт названия групп и ФИО: и своей записи, и всех, с кем у неё
    // общие пары, — расписание группы показывает преподавателя, преподавателя — группы
    private static final String SCHEDULE = "schedule";

    // выборки, собранные по всему справочнику: курс, уровень и поиск не привязать к id
    private static final List<String> GROUP_REGIONS = List.of("groups", "levels", "courses");
    private static final List<String> TEACHER_REGIONS = List.of("teachers");

    private final CacheService cacheService;
    private final MasterDirectoryService masterDirectoryService;

    public DirectoryCacheEvictor(
            CacheService cacheService,
            MasterDirectoryService masterDirectoryService
    ) {
        this.cacheService = cacheService;
        this.masterDirectoryService = masterDirectoryService;
    }

    public void onDirectoryChange(CacheEvent event) {
        switch (event.eventType()) {
            case GROUP -> evictGroups(event);
            case TEACHER -> evictTeachers(event);
            // остальные сущности справочника в модели расписания не участвуют
            default -> { }
        }
    }

    private void evictGroups(CacheEvent event) {
        if (event.unitType() == UnitType.CREATE) {
            log.info("Кеш не тронут: {} групп заведено, отданные ответы о них не знают", event.ids().size());
            return;
        }

        cacheService.clear(GROUP_REGIONS);
        cacheService.clear(List.of(SCHEDULE));

        if (event.unitType() == UnitType.DELETE) {
            cacheService.clear(List.of(DIRECTORY));
            log.info("Кеш: сброшены группы, расписание и соответствия — {} групп удалено", event.ids().size());
            return;
        }

        List<String> names = masterDirectoryService.groups(event.ids()).values().stream()
                .map(Group::getName)
                .filter(name -> name != null && !name.isBlank())
                .toList();

        // не досчитались имён — справочник не отдал запись; оставлять её соответствие
        // нельзя, а какое именно чистить, неизвестно
        if (names.size() < event.ids().size()) {
            cacheService.clear(List.of(DIRECTORY));
            log.info("Кеш: сброшены группы, расписание и соответствия целиком — справочник отдал {} имён из {}",
                    names.size(), event.ids().size());
            return;
        }

        names.forEach(name -> cacheService.evict(DIRECTORY, "group:" + name));
        log.info("Кеш: сброшены группы, расписание и {} соответствий по имени", names.size());
    }

    private void evictTeachers(CacheEvent event) {
        if (event.unitType() == UnitType.CREATE) {
            log.info("Кеш не тронут: {} преподавателей заведено, отданные ответы о них не знают",
                    event.ids().size());
            return;
        }

        cacheService.clear(TEACHER_REGIONS);
        cacheService.clear(List.of(SCHEDULE));

        if (event.unitType() == UnitType.DELETE) {
            cacheService.clear(List.of(DIRECTORY));
            log.info("Кеш: сброшены преподаватели, расписание и соответствия — {} записей удалено",
                    event.ids().size());
            return;
        }

        List<String> labels = masterDirectoryService.teachers(event.ids()).values().stream()
                .map(Teacher::getLabel)
                .filter(label -> label != null && !label.isBlank())
                .toList();

        if (labels.size() < event.ids().size()) {
            cacheService.clear(List.of(DIRECTORY));
            log.info("Кеш: сброшены преподаватели, расписание и соответствия целиком — справочник отдал {} ФИО из {}",
                    labels.size(), event.ids().size());
            return;
        }

        labels.forEach(label -> cacheService.evict(DIRECTORY, "teacher:" + label));
        log.info("Кеш: сброшены преподаватели, расписание и {} соответствий по ФИО", labels.size());
    }

}
