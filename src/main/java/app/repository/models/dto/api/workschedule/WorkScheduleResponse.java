package app.repository.models.dto.api.workschedule;

import java.time.LocalTime;
import java.util.List;

public record WorkScheduleResponse(
        Long id,
        Long versionId,
        Long teacherMasterId,
        String dayWeek,
        LocalTime startedAt,
        LocalTime finishedAt
) {

    public record Envelope(List<WorkScheduleResponse> workSchedules) {}

}
