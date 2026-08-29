package app.controller.api;

import app.repository.models.dto.api.schedule.ScheduleBatch;
import app.repository.models.dto.api.schedule.ScheduleResponse;
import app.repository.models.dto.mappers.ScheduleMapper;
import app.service.domain.persistence.SchedulePersistenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/schedule/")
public class ScheduleController {

  private final ScheduleMapper scheduleMapper;
  private final SchedulePersistenceService persistenceService;

  public ScheduleController(ScheduleMapper scheduleMapper, SchedulePersistenceService persistenceService) {
    this.scheduleMapper = scheduleMapper;
    this.persistenceService = persistenceService;
  }

  @PostMapping("/batch/schedule")
  @PreAuthorize("hasRole('ROLE_SCHEDULE')")
  public ResponseEntity<ScheduleResponse> setSchedule(@RequestBody ScheduleBatch batchRequest) {
    log.info("GET Запрос: /api/v1/schedule/batch");
    return ResponseEntity.ok(scheduleMapper.toScheduleResponse(persistenceService.updateAndSetSchedule(batchRequest)));
  }

}
