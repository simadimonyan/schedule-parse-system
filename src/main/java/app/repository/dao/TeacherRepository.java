package app.repository.dao;

import app.repository.models.entity.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeacherRepository extends JpaRepository<Teacher, Long> {

    Optional<Teacher> findByLabel(String label);

    Optional<List<Teacher>> findAllByDepartment(String department);

}
