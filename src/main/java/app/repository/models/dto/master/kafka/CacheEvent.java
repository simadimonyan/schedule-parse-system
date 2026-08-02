package app.repository.models.dto.master.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.List;

/**
 * Объявление мастер-сервиса об изменении справочника (топик {@code redis-cache-process}).
 *
 * <p>Копия контракта мастера, а не общая библиотека: сервисы деплоятся независимо, общий
 * класс связал бы их версии. Поля терпимы к незнакомым значениям — новый тип сущности в
 * мастере не должен ронять потребителя.
 *
 * <p>Именование полей у мастера: {@code event_type} — какая сущность изменилась,
 * {@code unit_type} — что с ней произошло.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CacheEvent(
        @JsonProperty("event_type")
        CacheType eventType,

        @JsonProperty("unit_type")
        UnitType unitType,

        List<Long> ids,
        String note
) implements Serializable {}
