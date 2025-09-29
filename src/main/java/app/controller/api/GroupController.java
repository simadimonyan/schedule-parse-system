package app.controller.api;

import app.repository.models.dto.api.group.GroupLevelsResponse;
import app.repository.models.dto.api.group.GroupResponse;
import app.repository.models.dto.api.group.GroupsResponse;
import app.repository.models.dto.api.schedule.ScheduleResponse;
import app.repository.models.dto.mappers.GroupMapper;
import app.repository.models.dto.mappers.ScheduleMapper;
import app.service.persistence.PersistenceService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/groups")
@SecurityRequirement(name = "Authorization")
public class GroupController {

    private final PersistenceService persistenceService;
    private final GroupMapper groupMapper;
    private final ScheduleMapper scheduleMapper;

    public GroupController(PersistenceService persistenceService, GroupMapper groupMapper, ScheduleMapper scheduleMapper) {
        this.persistenceService = persistenceService;
        this.groupMapper = groupMapper;
        this.scheduleMapper = scheduleMapper;
    }

    @GetMapping("/{group}")
    public ResponseEntity<GroupResponse> getGroup(@PathVariable("group") String groupName) {
        log.info("GET Запрос: /api/v1/groups/{}", groupName);
        return ResponseEntity.ok(groupMapper.toGroupResponse(persistenceService.getGroup(groupName)));
    }


    @GetMapping("/search")
    public ResponseEntity<GroupsResponse> search(@RequestParam("course") Integer course, @RequestParam(value = "level", required = false) String level) {
        log.info("GET Запрос: /api/v1/groups/search?course={}&level={}", course, level);
        return ResponseEntity.ok(groupMapper.toGroupsResponse(persistenceService.getGroups(course, level)));
    }

    @GetMapping("/levels")
    public ResponseEntity<GroupLevelsResponse> getLevels() {
        log.info("GET Запрос: /api/v1/groups/levels");
        return ResponseEntity.ok(new GroupLevelsResponse(persistenceService.getLevels()));
    }

    @GetMapping("/schedule")
    public ResponseEntity<ScheduleResponse> getSchedule(
            @RequestParam("group") String groupName,
            @RequestParam(value = "dayWeek", required = false) String dayWeek,
            @RequestParam("weekCount") Integer weekCount
    ) {
        log.info("GET Запрос: /api/v1/groups/schedule?group={}&dayWeek={}&weekCount={}", groupName, dayWeek, weekCount);
        return ResponseEntity.ok(scheduleMapper.toScheduleResponse(persistenceService.getGroupSchedule(groupName, dayWeek, weekCount)));
    }

}
