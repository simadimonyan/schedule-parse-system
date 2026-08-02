package app.repository.models.dto.master;

import java.io.Serializable;
import java.util.List;

/** Тело поиска записей мастер-сервиса по естественному ключу. */
public record MasterKeysRequest(
        List<String> keys
) implements Serializable {}
