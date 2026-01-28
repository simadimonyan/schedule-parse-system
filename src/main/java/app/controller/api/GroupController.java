package app.controller.api;

import app.repository.models.dto.api.group.GroupCoursesResponse;
import app.repository.models.dto.api.group.GroupLevelsResponse;
import app.repository.models.dto.api.group.GroupResponse;
import app.repository.models.dto.api.group.GroupsResponse;
import app.repository.models.dto.api.schedule.ScheduleResponse;
import app.repository.models.dto.mappers.GroupMapper;
import app.repository.models.dto.mappers.ScheduleMapper;
import app.service.persistence.SchedulePersistenceService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/groups")
@SecurityRequirement(name = "Authorization")
public class GroupController {

    private final SchedulePersistenceService schedulePersistenceService;
    private final GroupMapper groupMapper;
    private final ScheduleMapper scheduleMapper;

    public GroupController(SchedulePersistenceService schedulePersistenceService, GroupMapper groupMapper, ScheduleMapper scheduleMapper) {
        this.schedulePersistenceService = schedulePersistenceService;
        this.groupMapper = groupMapper;
        this.scheduleMapper = scheduleMapper;
    }

    @GetMapping("/{group}")
    public ResponseEntity<GroupResponse> getGroup(@PathVariable("group") String groupName) {
        log.info("GET Запрос: /api/v1/groups/{}", groupName);
        return ResponseEntity.ok(groupMapper.toGroupResponse(schedulePersistenceService.getGroup(groupName)));
    }

    @GetMapping("/search")
    public ResponseEntity<GroupsResponse> search(@RequestParam("course") Integer course, @RequestParam(value = "level", required = false) String level) {
        log.info("GET Запрос: /api/v1/groups/search?course={}&level={}", course, level);
        return ResponseEntity.ok(groupMapper.toGroupsResponse(schedulePersistenceService.getGroups(course, level)));
    }

    @GetMapping("/levels")
    public ResponseEntity<GroupLevelsResponse> getLevels(@RequestParam("course") Integer course) {
        log.info("GET Запрос: /api/v1/groups/levels?course={}", course);
        return ResponseEntity.ok(new GroupLevelsResponse(schedulePersistenceService.getLevels(course)));
    }

    @GetMapping("/courses")
    public ResponseEntity<GroupCoursesResponse> getCourses() {
        log.info("GET Запрос: /api/v1/groups/courses");
        return ResponseEntity.ok(new GroupCoursesResponse(schedulePersistenceService.getCourses()));
    }

    @GetMapping("/schedule")
    public ResponseEntity<ScheduleResponse> getSchedule(
            @RequestParam("group") String groupName,
            @RequestParam(value = "dayWeek", required = false) String dayWeek,
            @RequestParam(value = "date", required = false) String date,
            @RequestParam("weekCount") Integer weekCount
    ) {
        log.info("GET Запрос: /api/v1/groups/schedule?group={}&dayWeek={}&date={}&weekCount={}", groupName, dayWeek, date, weekCount);
        return ResponseEntity.ok(scheduleMapper.toScheduleResponse(schedulePersistenceService.getGroupSchedule(groupName, dayWeek, date, weekCount)));
    }

}
