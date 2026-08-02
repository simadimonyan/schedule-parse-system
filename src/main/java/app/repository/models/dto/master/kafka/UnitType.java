package app.repository.models.dto.master.kafka;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

/** Что произошло с записью справочника в мастер-сервисе. */
public enum UnitType {

    CREATE,
    UPDATE,
    DELETE,

    @JsonEnumDefaultValue
    UNKNOWN

}
