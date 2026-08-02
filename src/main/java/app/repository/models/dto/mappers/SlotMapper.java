package app.repository.models.dto.mappers;

import app.repository.models.dto.api.slot.SlotResponse;
import app.repository.models.entity.TimeSlot;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SlotMapper {

    public SlotResponse toResponse(TimeSlot slot) {
        if (slot == null) return null;

        return new SlotResponse(
                slot.getId(),
                slot.getVersion() == null ? null : slot.getVersion().getId(),
                slot.getDayWeek(),
                slot.getWeekCount(),
                slot.getLessonCount(),
                slot.getTimeRange(),
                slot.getStartedAt(),
                slot.getFinishedAt());
    }

    public SlotResponse.Envelope toEnvelope(List<TimeSlot> slots) {
        return new SlotResponse.Envelope(slots.stream().map(this::toResponse).toList());
    }

}
