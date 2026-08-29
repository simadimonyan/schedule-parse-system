package app.repository;

import app.repository.dao.TimeSlotRepository;
import app.repository.models.entity.TimeSlot;
import app.repository.models.entity.Version;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Подбирает слот парам, загруженным до того, как сетка стала отдельной сущностью.
 *
 * <p>Раньше день недели, чётность, номер пары и время лежали колонками самой пары. Теперь их
 * единственное место — {@link TimeSlot}, а выборки расписания ходят к нему джойном. Строки,
 * записанные до этого, ссылки на слот не имеют: без переноса они перестали бы находиться
 * вовсе — расписание пропало бы при первом же старте после обновления, ровно как это было с
 * версиями (см. {@link VersionInitializer}).
 *
 * <p>Читает старые колонки напрямую запросом к базе: в сущности их больше нет, а в таблице
 * они остаются — {@code ddl-auto=update} колонки не удаляет. Снести их можно руками, когда
 * перенос отработает; сам он этого не делает, потому что удалять данные при старте
 * приложения нельзя.
 *
 * <p>Работает один раз: после прохода пар без слота не остаётся. На новой базе колонок нет
 * и переносить нечего — проверка это видит и молчит.
 */
@Slf4j
@Order(2)
@Component
public class SlotBackfill implements ApplicationRunner {

    /** Старые колонки пары — по ним и опознаётся база, которую надо переносить. */
    private static final List<String> LEGACY_COLUMNS =
            List.of("day_week", "week_count", "lesson_count", "time_period");

    private final TimeSlotRepository timeSlotRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public SlotBackfill(TimeSlotRepository timeSlotRepository) {
        this.timeSlotRepository = timeSlotRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!legacyColumnsPresent()) return;

        List<Object[]> orphans = legacyPairs();
        if (orphans.isEmpty()) {
            log.info("Пар без слота нет — сетка полная");
            return;
        }

        // по версиям: слот принадлежит своей версии, и одна ячейка «Понедельник, 1 пара»
        // существует в каждой из них отдельно
        Map<Long, List<Object[]>> byVersion = new LinkedHashMap<>();
        int versionless = 0;
        for (Object[] row : orphans) {
            Long versionId = number(row[1]);
            // версию таким парам раздаёт VersionInitializer, он идёт раньше. Если строка
            // всё-таки без версии, слот ей завести не в чем — её и так никто не найдёт
            if (versionId == null) {
                versionless++;
                continue;
            }
            byVersion.computeIfAbsent(versionId, id -> new ArrayList<>()).add(row);
        }

        if (versionless > 0) {
            log.error("{} пар остались без версии — слот им не подобран", versionless);
        }

        int slots = 0;
        int pairs = 0;
        for (Map.Entry<Long, List<Object[]>> entry : byVersion.entrySet()) {
            int[] done = backfillVersion(entry.getKey(), entry.getValue());
            slots += done[0];
            pairs += done[1];
        }

        log.info("Перенос сетки: {} пар расставлено по {} новым слотам", pairs, slots);
    }

    /**
     * Расставляет пары одной версии.
     *
     * @return сколько слотов заведено и сколько пар получило ссылку
     */
    private int[] backfillVersion(Long versionId, List<Object[]> rows) {
        Map<String, TimeSlot> grid = new HashMap<>();
        for (TimeSlot slot : timeSlotRepository.findGrid(versionId)) {
            grid.put(key(slot.getDayWeek(), slot.getWeekCount(), slot.getLessonCount(), slot.getTimeRange()), slot);
        }

        Version version = entityManager.getReference(Version.class, versionId);

        // сначала вся сетка, потом ссылки: слоту нужен идентификатор, а он появляется
        // только после сохранения
        Map<String, List<Long>> byKey = new LinkedHashMap<>();
        List<TimeSlot> created = new ArrayList<>();

        for (Object[] row : rows) {
            String dayWeek = string(row[2]);
            Integer weekCount = integer(row[3]);
            Integer lessonCount = integer(row[4]);
            String timeRange = string(row[5]) == null ? "" : string(row[5]).trim();

            String key = key(dayWeek, weekCount, lessonCount, timeRange);
            if (!grid.containsKey(key)) {
                TimeSlot slot = TimeSlot.draft(dayWeek, weekCount, lessonCount, timeRange);
                slot.setVersion(version);
                grid.put(key, slot);
                created.add(slot);
            }
            byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(number(row[0]));
        }

        if (!created.isEmpty()) timeSlotRepository.saveAll(created);
        entityManager.flush();

        int updated = 0;
        for (Map.Entry<String, List<Long>> cell : byKey.entrySet()) {
            updated += entityManager
                    .createNativeQuery("UPDATE schedule_table SET slot_id = :slotId WHERE schedule_id IN (:ids)")
                    .setParameter("slotId", grid.get(cell.getKey()).getId())
                    .setParameter("ids", cell.getValue())
                    .executeUpdate();
        }

        log.info("Версия {}: заведено {} слотов, расставлено {} пар", versionId, created.size(), updated);
        return new int[]{created.size(), updated};
    }

    /**
     * Есть ли в таблице старые колонки.
     *
     * <p>Проверка обязательна: на базе, поднятой уже без них, запрос за {@code day_week}
     * упал бы, а вместе с ним и старт приложения.
     */
    private boolean legacyColumnsPresent() {
        Object count = entityManager.createNativeQuery("""
                        SELECT count(*) FROM information_schema.columns
                        WHERE table_name = 'schedule_table' AND column_name IN (:columns)
                        """)
                .setParameter("columns", LEGACY_COLUMNS)
                .getSingleResult();

        return number(count) != null && number(count) == LEGACY_COLUMNS.size();
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> legacyPairs() {
        return entityManager.createNativeQuery("""
                SELECT schedule_id, version_id, day_week, week_count, lesson_count, time_period
                FROM schedule_table WHERE slot_id IS NULL
                """).getResultList();
    }

    private static String key(String dayWeek, Integer weekCount, Integer lessonCount, String timeRange) {
        return dayWeek + "|" + weekCount + "|" + lessonCount + "|" + (timeRange == null ? "" : timeRange);
    }

    private static Long number(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static Integer integer(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }

    private static String string(Object value) {
        return value == null ? null : value.toString();
    }

}
