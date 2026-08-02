package app.repository.models.dto.master;

import java.io.Serializable;
import java.util.List;

/**
 * Тело запроса на чтение и мягкое удаление у мастер-сервиса — одинаковое для всех сущностей.
 *
 * <p>Мастер принимает это тело в том числе у {@code GET /list/batch}. Тело у GET умеет не
 * каждый клиент, поэтому запрос собирается через {@code WebClient.method(HttpMethod.GET)}.
 */
public record MasterIdsRequest(
        List<Long> ids
) implements Serializable {}
