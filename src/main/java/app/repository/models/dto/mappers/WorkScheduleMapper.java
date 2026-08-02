package app.repository.models.dto.mappers;

import app.repository.models.dto.api.workschedule.WorkScheduleResponse;
import app.repository.models.entity.WorkSchedule;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WorkScheduleMapper {

    public WorkScheduleResponse toResponse(WorkSchedule work) {
        return new WorkScheduleResponse(
                work.getId(),
                work.getVersion() == null ? null : work.getVersion().getId(),
                work.getTeacherMasterId(),
                work.getDayWeek(),
                work.getStartedAt(),
                work.getFinishedAt());
    }

    public WorkScheduleResponse.Envelope toEnvelope(List<WorkSchedule> workSchedules) {
        return new WorkScheduleResponse.Envelope(workSchedules.stream().map(this::toResponse).toList());
    }

}
