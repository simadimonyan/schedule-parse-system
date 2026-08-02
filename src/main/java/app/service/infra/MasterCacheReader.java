package app.service.infra;

import app.repository.models.dto.master.MasterGroupView;
import app.repository.models.dto.master.MasterTeacherView;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Чтение справочников мастер-сервиса через его общий кеш в Redis.
 *
 * <p>Мастер — владелец данных — сам держит их слепок в Redis под ключами
 * {@code master:{сущность}:{id}} и обновляет его при каждой записи. Префикс — объявленный
 * контракт с читающими сервисами, менять его в одностороннем порядке нельзя.
 *
 * <p>Событие из Kafka как раз и означает, что слепок только что обновлён, поэтому ходить
 * за теми же данными по HTTP незачем. HTTP остаётся запасным путём: кеш не источник
 * правды, и любая его проблема — промах ключа, чужой формат значения, недоступный Redis —
 * деградирует в обычный запрос к мастеру, а не в ошибку.
 */
@Slf4j
@Component
public class MasterCacheReader {

    private static final String GROUP_PREFIX = "master:group:";
    private static final String TEACHER_PREFIX = "master:teacher:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final MasterServiceManager masterServiceManager;

    public MasterCacheReader(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            MasterServiceManager masterServiceManager
    ) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.masterServiceManager = masterServiceManager;
    }

    /** Группы справочника: из общего кеша, промахи — из мастера по HTTP. */
    public List<MasterGroupView> getGroups(List<Long> ids) {
        return readThrough(ids, GROUP_PREFIX, MasterGroupView.class,
                MasterGroupView::id, masterServiceManager::getGroups);
    }

    /** Преподаватели справочника: из общего кеша, промахи — из мастера по HTTP. */
    public List<MasterTeacherView> getTeachers(List<Long> ids) {
        return readThrough(ids, TEACHER_PREFIX, MasterTeacherView.class,
                MasterTeacherView::id, masterServiceManager::getTeachers);
    }

    /**
     * Пачка забирается одним {@code MGET}: запрос на сотню идентификаторов не должен
     * превращаться в сотню поездок в Redis, иначе кеш не окупается.
     */
    private <T> List<T> readThrough(
            List<Long> ids,
            String prefix,
            Class<T> type,
            Function<T, Long> idOf,
            Function<List<Long>, List<T>> loadMissing
    ) {
        List<Long> unique = ids.stream().distinct().toList();
        if (unique.isEmpty()) return List.of();

        Map<Long, T> found = new LinkedHashMap<>();
        List<Long> misses = new ArrayList<>();

        List<String> raw = null;
        try {
            raw = redis.opsForValue().multiGet(unique.stream().map(id -> prefix + id).toList());
        }
        catch (RuntimeException e) {
            log.warn("Общий кеш мастер-сервиса недоступен, читаем по HTTP: {}", e.getMessage());
        }

        if (raw == null) {
            misses.addAll(unique);
        }
        else {
            // multiGet отдаёт значения строго в порядке ключей, с null на промахах —
            // на этом и держится сопоставление по индексу, порядок unique менять нельзя
            for (int i = 0; i < unique.size(); i++) {
                Long id = unique.get(i);
                String json = i < raw.size() ? raw.get(i) : null;

                if (json == null) {
                    misses.add(id);
                    continue;
                }
                try {
                    found.put(id, objectMapper.readValue(json, type));
                }
                catch (Exception e) {
                    // формат значения мог смениться с прошлого релиза мастера — перечитаем по HTTP
                    log.warn("Не удалось разобрать {}{}: {}", prefix, id, e.getMessage());
                    misses.add(id);
                }
            }
        }

        if (!misses.isEmpty()) {
            log.debug("Промахов общего кеша: {} из {}", misses.size(), unique.size());
            loadMissing.apply(misses).forEach(view -> found.put(idOf.apply(view), view));
        }

        return unique.stream().map(found::get).filter(Objects::nonNull).toList();
    }

}
