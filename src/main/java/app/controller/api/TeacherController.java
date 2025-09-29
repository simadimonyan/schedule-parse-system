package app.controller.api;

import app.repository.models.dto.api.schedule.ScheduleResponse;
import app.repository.models.dto.api.teacher.TeacherResponse;
import app.repository.models.dto.api.teacher.TeachersResponse;
import app.repository.models.dto.mappers.ScheduleMapper;
import app.repository.models.dto.mappers.TeacherMapper;
import app.service.persistence.PersistenceService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/teachers/")
@SecurityRequirement(name = "Authorization")
public class TeacherController {

    private final PersistenceService persistenceService;
    private final TeacherMapper teacherMapper;
    private final ScheduleMapper scheduleMapper;

    @Autowired
    public TeacherController(PersistenceService persistenceService, TeacherMapper teacherMapper, ScheduleMapper scheduleMapper) {
        this.persistenceService = persistenceService;
        this.teacherMapper = teacherMapper;
        this.scheduleMapper = scheduleMapper;
    }

    @GetMapping("/{teacher}")
    public ResponseEntity<TeacherResponse> getTeacher(@PathVariable("teacher") String teacherLabel) {
        log.info("GET Запрос: /api/v1/teachers/{}", teacherLabel);
        return ResponseEntity.ok(teacherMapper.toTeacherResponse(persistenceService.getTeacher(teacherLabel)));
    }

    @GetMapping("/search")
    public ResponseEntity<TeachersResponse> search(@RequestParam(value = "department", required = false) String department) {
        log.info("GET Запрос: /api/v1/teachers?department={}", department);
        return ResponseEntity.ok(teacherMapper.toTeachersResponse(persistenceService.getTeachers(department)));
    }

    @GetMapping("/schedule")
    public ResponseEntity<ScheduleResponse> getSchedule(
            @RequestParam("teacher") String teacherName,
            @RequestParam(value = "dayWeek", required = false) String dayWeek,
            @RequestParam("weekCount") Integer weekCount
    ) {
        log.info("GET Запрос: /api/v1/teachers/schedule?teacher={}&dayWeek={}&weekCount={}", teacherName, dayWeek, weekCount);
        return ResponseEntity.ok(scheduleMapper.toScheduleResponse(persistenceService.getTeacherSchedule(teacherName, dayWeek, weekCount)));
    }

}
