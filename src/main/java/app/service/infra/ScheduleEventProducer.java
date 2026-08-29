package app.service.infra;

import app.repository.models.dto.event.ScheduleEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Рассылка объявлений сервиса об изменении расписания.
 *
 * <p>Отправка идёт после коммита, а не из самого метода: событие необратимо, и откатить его
 * вслед за откатом транзакции нечем. Отправь мы его раньше — подписчик пришёл бы за
 * расписанием, которого в базе так и не появилось. Поэтому доменные сервисы объявляют
 * событие через {@code ApplicationEventPublisher}, а до Kafka оно доходит здесь и только
 * когда запись состоялась.
 *
 * <p>{@code fallbackExecution = true} — для тех мест, где транзакции нет вовсе: переключение
 * чётности недели правит одну строку настроек без неё. Без этого флага такое событие молча
 * никуда бы не ушло.
 *
 * <p>Отправка не должна ронять запрос: данные уже в базе, а недоставленное событие подписчики
 * переживут — их кеш протухнет по TTL. Тот же расчёт, что у {@code CacheProducer} мастера,
 * от которого этот сервис принимает события справочников.
 */
@Slf4j
@Component
public class ScheduleEventProducer {

    private final KafkaTemplate<String, Object> scheduleEventKafkaTemplate;
    private final String topic;

    public ScheduleEventProducer(
            KafkaTemplate<String, Object> scheduleEventKafkaTemplate,
            @Value("${schedule.kafka.topic}") String topic
    ) {
        this.scheduleEventKafkaTemplate = scheduleEventKafkaTemplate;
        this.topic = topic;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onScheduleEvent(ScheduleEvent event) {
        if (event == null || event.eventType() == null) return;

        try {
            log.info("Событие расписания: {} версия {}", event.eventType(), event.versionId());
            scheduleEventKafkaTemplate.send(topic, event.key(), event)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            log.error("Не удалось отправить {} в {}", event, topic, error);
                        }
                    });
        } catch (RuntimeException e) {
            log.error("Не удалось отправить {} в {}", event, topic, e);
        }
    }

}
