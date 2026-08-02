package app.repository.models.dto.mappers;

import app.repository.models.dto.api.change.ChangeResponse;
import app.repository.models.entity.Change;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ChangeMapper {

    private final SlotMapper slotMapper;

    public ChangeMapper(SlotMapper slotMapper) {
        this.slotMapper = slotMapper;
    }

    public ChangeResponse toResponse(Change change) {
        return new ChangeResponse(
                change.getId(),
                change.getVersion() == null ? null : change.getVersion().getId(),
                change.getChangeType(),
                change.getChangeDate(),
                change.getSchedule() == null ? null : change.getSchedule().getId(),
                slotMapper.toResponse(change.getSlot()),
                change.getSubjectMasterId(),
                change.getTeacherMasterId(),
                change.getGroupMasterId(),
                change.getAuditoriumMasterId(),
                change.getDepartmentMasterId(),
                change.getPayload(),
                change.getCreatedAt());
    }

    public ChangeResponse.Envelope toEnvelope(List<Change> changes) {
        return new ChangeResponse.Envelope(changes.stream().map(this::toResponse).toList());
    }

}
