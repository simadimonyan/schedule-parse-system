package app.configuration;

import app.repository.models.dto.master.kafka.CacheEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

/**
 * Приём объявлений мастер-сервиса об изменении справочников.
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

}
