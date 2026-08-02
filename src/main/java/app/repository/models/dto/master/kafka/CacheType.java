package app.repository.models.dto.master.kafka;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

/**
 * Сущность справочника, которую затронуло изменение в мастер-сервисе.
 *
 * <p>Расписанию интересны только {@link #GROUP} и {@link #TEACHER}, остальные значения
 * перечислены, чтобы событие вообще разобралось. {@link #UNKNOWN} ловит типы, добавленные
 * в мастере позже: неизвестная сущность — повод пропустить событие, а не уронить слушателя
 * (см. {@code spring.jackson.deserialization.read-unknown-enum-values-using-default-value}).
 */
public enum CacheType {

    APPOINTMENT,
    AUDITORIUM,
    BLOCK,
    DEPARTMENT,
    EQUIPMENT,
    FACULTY,
    GROUP,
    INVENTORY,
    REQUIREMENT,
    SUBJECT,
    TEACHER,

    @JsonEnumDefaultValue
    UNKNOWN

}
