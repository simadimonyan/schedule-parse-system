package app.repository.models.dto.api.slot;

import java.time.LocalTime;
import java.util.List;

public record SlotResponse(
        Long id,
        Long versionId,
        String dayWeek,
        Integer weekCount,
        Integer lessonCount,
        String timeRange,
        LocalTime startedAt,
        LocalTime finishedAt
) {

    public record Envelope(List<SlotResponse> slots) {}

}
