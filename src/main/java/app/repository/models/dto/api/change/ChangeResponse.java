package app.repository.models.dto.api.change;

import app.repository.models.dto.api.slot.SlotResponse;
import app.repository.models.entity.ChangeType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record ChangeResponse(
        Long id,
        Long versionId,
        ChangeType changeType,
        LocalDate changeDate,
        Long scheduleId,
        SlotResponse slot,
        Long subjectMasterId,
        Long teacherMasterId,
        Long groupMasterId,
        Long auditoriumMasterId,
        Long departmentMasterId,
        Map<String, Object> payload,
        Instant createdAt
) {

    public record Envelope(List<ChangeResponse> changes) {}

}
