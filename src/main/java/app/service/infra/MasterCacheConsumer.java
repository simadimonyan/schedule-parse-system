package app.service.infra;

import app.repository.models.dto.master.kafka.CacheEvent;
import app.repository.models.dto.master.kafka.CacheType;
import app.service.cache.DirectoryCacheEvictor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Слушатель объявлений мастер-сервиса об изменении справочников.
 *
 * <p>Расписанию интересны только группы и преподаватели — остальные сущности справочника
 * (аудитории, кафедры, предметы) в его модели не участвуют и пропускаются.
 *
 * <p>Данных событие не приносит, но их и не нужно: расписание ссылается на справочник
 * идентификаторами, а названия, курс и уровень попадают в выдачу прямо из него. Устареть
 * может только кеш сервиса — что именно из него сбросить, решает
 * {@link DirectoryCacheEvictor}.
 */
@Slf4j
@Component
public class MasterCacheConsumer {

    private final DirectoryCacheEvictor cacheEvictor;

    public MasterCacheConsumer(DirectoryCacheEvictor cacheEvictor) {
        this.cacheEvictor = cacheEvictor;
    }

    @KafkaListener(
            topics = "${master.kafka.topic}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "masterCacheListenerFactory"
    )
    public void onCacheEvent(CacheEvent event) {
        if (event == null || event.eventType() == null || event.unitType() == null) {
            log.warn("Получено нечитаемое событие мастер-сервиса — пропущено");
            return;
        }
        if (event.ids() == null || event.ids().isEmpty()) return;
        if (event.eventType() != CacheType.GROUP && event.eventType() != CacheType.TEACHER) return;

        log.info("Событие мастер-сервиса: {} {} для {} записей",
                event.eventType(), event.unitType(), event.ids().size());

        cacheEvictor.onDirectoryChange(event);
    }

}
