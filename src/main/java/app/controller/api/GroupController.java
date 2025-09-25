package app.controller.api;

import app.repository.dao.GroupRepository;
import app.repository.dao.ScheduleRepository;
import app.repository.models.dto.api.group.GroupLevelsResponse;
import app.repository.models.dto.api.group.GroupResponse;
import app.repository.models.dto.api.group.GroupsResponse;
import app.repository.models.dto.api.schedule.ScheduleResponse;
import app.repository.models.dto.mappers.GroupMapper;
import app.repository.models.dto.mappers.ScheduleMapper;
import app.repository.models.entity.Group;
import app.repository.models.entity.Schedule;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/groups")
@SecurityRequirement(name = "Authorization")
public class GroupController {

    private final GroupRepository groupRepository;
    private final ScheduleRepository scheduleRepository;
    private final GroupMapper groupMapper;
    private final ScheduleMapper scheduleMapper;

    public GroupController(
            GroupRepository groupRepository,
            ScheduleRepository scheduleRepository,
            GroupMapper groupMapper,
            ScheduleMapper scheduleMapper
    ) {
        this.groupRepository = groupRepository;
        this.scheduleRepository = scheduleRepository;
        this.groupMapper = groupMapper;
        this.scheduleMapper = scheduleMapper;
    }

    @GetMapping("/{group}")
    public ResponseEntity<GroupResponse> getGroup(@PathVariable("group") String groupName) throws EntityNotFoundException {
        Group group = groupRepository.findByName(groupName).orElse(null);
        if (group == null) throw new EntityNotFoundException(String.format("Группа %s не найдена", groupName));
        return ResponseEntity.ok(groupMapper.toGroupResponse(group));
    }

    @GetMapping("/search")
    public ResponseEntity<GroupsResponse> search(@RequestParam("course") Integer course, @RequestParam(value = "level", required = false) String level) {
        List<Group> groups;
        if (level == null)
            groups = groupRepository.findAllByCourse(course).orElse(new ArrayList<>());
        else
            groups = groupRepository.findAllByCourseAndLevel(course, level).orElse(new ArrayList<>());
        return ResponseEntity.ok(groupMapper.toGroupsResponse(groups));
    }

    @GetMapping("/levels")
    public ResponseEntity<GroupLevelsResponse> getLevels() {
        return ResponseEntity.ok(new GroupLevelsResponse(groupRepository.findDistinctLevels().orElse(new ArrayList<>())));
    }

    @GetMapping("/schedule")
    public ResponseEntity<ScheduleResponse> getTodaySchedule(
            @RequestParam("group") String groupName,
            @RequestParam(value = "dayWeek", required = false) String dayWeek,
            @RequestParam("weekCount") Integer weekCount
    ) {
        List<Schedule> schedule;
        if (dayWeek == null)
            schedule = scheduleRepository.findAllByGroupNameAndWeekCount(groupName, weekCount)
                    .orElse(new ArrayList<>());
        else
            schedule = scheduleRepository.findAllByGroupNameAndDayWeekAndWeekCount(groupName, dayWeek, weekCount)
                    .orElse(new ArrayList<>());
        return ResponseEntity.ok(scheduleMapper.toScheduleResponse(schedule));
    }

}
