package app.repository.dao;

import app.repository.models.entity.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TeacherRepository extends JpaRepository<Teacher, Long> {

    Optional<Teacher> findAllByLabel(String label);

    Optional<Teacher> findAllByDepartment(String department);

}
