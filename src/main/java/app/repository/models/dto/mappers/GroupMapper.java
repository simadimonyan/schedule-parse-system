package app.repository.models.dto.mappers;

import app.repository.models.dto.api.group.GroupResponse;
import app.repository.models.dto.api.group.GroupsResponse;
import app.repository.models.entity.Group;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class GroupMapper {

    public GroupResponse toGroupResponse(Group group) {
        return new GroupResponse(group.getId(), group.getName(), group.getCourse(), group.getLevel());
    }

    public GroupsResponse toGroupsResponse(List<Group> groups) {
        List<GroupResponse> units = new ArrayList<>();
        for (Group g : groups) {
            units.add(toGroupResponse(g));
        }
        return new GroupsResponse(units);
    }

}
