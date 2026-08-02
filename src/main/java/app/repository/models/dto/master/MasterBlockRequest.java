package app.repository.models.dto.master;

import java.io.Serializable;
import java.util.List;

/** Тело создания корпусов в мастер-сервисе. */
public record MasterBlockRequest(
        List<BlockItem> items
) implements Serializable {

    /**
     * Корпус берётся из префикса номера аудитории: «1-114а» — корпус «1». Ни адреса, ни
     * прочих реквизитов в расписании нет, поэтому заполняется только название.
     */
    public record BlockItem(
            Long id,
            String name,
            String address,
            String note
    ) implements Serializable {}

}
