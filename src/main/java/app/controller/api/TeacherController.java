package app.controller.api;

import app.repository.dao.ScheduleRepository;
import app.repository.dao.TeacherRepository;
import app.repository.models.dto.api.schedule.ScheduleResponse;
import app.repository.models.dto.api.teacher.TeacherResponse;
import app.repository.models.dto.api.teacher.TeachersResponse;
import app.repository.models.dto.mappers.ScheduleMapper;
import app.repository.models.dto.mappers.TeacherMapper;
import app.repository.models.entity.Schedule;
import app.repository.models.entity.Teacher;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/teachers/")
@SecurityRequirement(name = "Authorization")
public class TeacherController {

    private final TeacherRepository teacherRepository;
    private final ScheduleRepository scheduleRepository;
    private final TeacherMapper teacherMapper;
    private final ScheduleMapper scheduleMapper;

    @Autowired
    public TeacherController(
            TeacherRepository teacherRepository,
            ScheduleRepository scheduleRepository,
            TeacherMapper teacherMapper,
            ScheduleMapper scheduleMapper
    ) {
        this.teacherRepository = teacherRepository;
        this.scheduleRepository = scheduleRepository;
        this.teacherMapper = teacherMapper;
        this.scheduleMapper = scheduleMapper;
    }

    @GetMapping("/{teacher}")
    public ResponseEntity<TeacherResponse> getTeacher(@PathVariable("teacher") String teacherLabel) throws EntityNotFoundException {
        Teacher group = teacherRepository.findByLabel(teacherLabel).orElse(null);
        if (group == null) throw new EntityNotFoundException(String.format("Преподаватель %s не найден", teacherLabel));
        return ResponseEntity.ok(teacherMapper.toTeacherResponse(group));
    }

    @GetMapping("/search")
    public ResponseEntity<TeachersResponse> search(@RequestParam(value = "department", required = false) String department) {
        log.info("1234567812345678");
        List<Teacher> teachers;
        if (department == null)
            teachers = teacherRepository.findAll();
        else
            teachers = teacherRepository.findAllByDepartment(department).orElse(new ArrayList<>());
        return ResponseEntity.ok(teacherMapper.toTeachersResponse(teachers));
    }

    @GetMapping("/schedule")
    public ResponseEntity<ScheduleResponse> getTodaySchedule(
            @RequestParam("teacher") String teacherName,
            @RequestParam(value = "dayWeek", required = false) String dayWeek,
            @RequestParam("weekCount") Integer weekCount
    ) {
        List<Schedule> schedule;
        if (dayWeek == null)
            schedule = scheduleRepository.findAllByTeacherLabelAndWeekCount(teacherName, weekCount)
                    .orElse(new ArrayList<>());
        else
            schedule = scheduleRepository.findAllByTeacherLabelAndDayWeekAndWeekCount(teacherName, dayWeek, weekCount)
                    .orElse(new ArrayList<>());
        return ResponseEntity.ok(scheduleMapper.toScheduleResponse(schedule));
    }

}
