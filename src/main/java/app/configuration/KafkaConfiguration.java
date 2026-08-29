package app.configuration;

import app.repository.models.dto.master.kafka.CacheEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

/**
 * Приём объявлений мастер-сервиса об изменении справочников и отправка своих.
 *
 * <p>Мастер шлёт сообщения с заголовком типа, указывающим на его собственный класс, —
 * такого класса здесь нет и быть не должно. Поэтому заголовок игнорируется, а тело всегда
 * читается как {@link CacheEvent}: контракт задаёт топик, а не имя чужого класса.
 *
 * <p>Разбор ведётся общим {@code ObjectMapper} приложения — вместе с ним подхватывается
 * терпимость к незнакомым значениям перечислений
 * ({@code read-unknown-enum-values-using-default-value}): новая сущность в справочнике
 * мастера не должна ронять слушателя.
 */
@Configuration
public class KafkaConfiguration {

    @Bean
    public ConsumerFactory<String, CacheEvent> masterCacheConsumerFactory(
            KafkaProperties properties,
            ObjectMapper objectMapper
    ) {
        JsonDeserializer<CacheEvent> deserializer = new JsonDeserializer<>(CacheEvent.class, objectMapper, false);
        deserializer.setUseTypeHeaders(false);

        return new DefaultKafkaConsumerFactory<>(
                properties.buildConsumerProperties(null),
                new StringDeserializer(),
                // битое сообщение не должно вставать поперёк топика: слушатель получит null
                // и пропустит запись, а разбор продолжится со следующей
                new ErrorHandlingDeserializer<>(deserializer)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CacheEvent> masterCacheListenerFactory(
            ConsumerFactory<String, CacheEvent> masterCacheConsumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, CacheEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(masterCacheConsumerFactory);
        return factory;
    }

    /**
     * Отправитель собственных событий расписания.
     *
     * <p>Заголовок с именем класса не пишется намеренно — по той же причине, по которой он
     * игнорируется на приёме выше: {@code app.repository.models.dto.event.ScheduleEvent} —
     * имя класса этого сервиса, и подписчику, у которого своя копия контракта, оно ничего не
     * говорит, а Spring-потребителю без такого класса — ломает разбор. Контракт задаёт топик.
     *
     * <p>Разбор и сборка идут одним {@code ObjectMapper} приложения: даты уезжают в том же
     * виде, в каком приходят в API, а не числами эпохи.
     */
    @Bean
    public ProducerFactory<String, Object> scheduleEventProducerFactory(
            KafkaProperties properties,
            ObjectMapper objectMapper
    ) {
        JsonSerializer<Object> serializer = new JsonSerializer<>(objectMapper);
        serializer.setAddTypeInfo(false);

        return new DefaultKafkaProducerFactory<>(
                properties.buildProducerProperties(null),
                new StringSerializer(),
                serializer);
    }

    @Bean
    public KafkaTemplate<String, Object> scheduleEventKafkaTemplate(
            ProducerFactory<String, Object> scheduleEventProducerFactory
    ) {
        return new KafkaTemplate<>(scheduleEventProducerFactory);
    }

}
