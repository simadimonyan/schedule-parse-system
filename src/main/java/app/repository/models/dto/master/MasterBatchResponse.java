package app.repository.models.dto.master;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;
import java.util.List;

/**
 * Общий конверт ответа мастер-сервиса. Одинаков для чтения, создания, обновления и удаления.
 *
 * <p>Поле {@code updated} называется так во всех операциях, включая чтение — это особенность
 * контракта мастера, а не признак того, что записи менялись.
 *
 * <p>Частичный успех — норма: HTTP 200 может нести и обработанные записи, и ошибки уровня
 * записи в {@code errors}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MasterBatchResponse<T>(
        Integer processed,
        Integer failed,
        List<T> updated,
        List<BatchError> errors
) implements Serializable {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BatchError(
            String message,
            String code
    ) implements Serializable {}

}
