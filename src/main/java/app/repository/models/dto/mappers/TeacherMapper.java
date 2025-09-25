package app.repository.models.dto.mappers;

import app.repository.models.dto.api.teacher.TeacherResponse;
import app.repository.models.dto.api.teacher.TeachersResponse;
import app.repository.models.entity.Teacher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TeacherMapper {

    public TeacherResponse toTeacherResponse(Teacher teacher) {
        return new TeacherResponse(teacher.getId(), teacher.getLabel(), teacher.getDepartment());
    }

    public TeachersResponse toTeachersResponse(List<Teacher> teachers) {
        List<TeacherResponse> units = new ArrayList<>();
        for (Teacher t : teachers) {
            units.add(toTeacherResponse(t));
        }
        return new TeachersResponse(units);
    }

}
