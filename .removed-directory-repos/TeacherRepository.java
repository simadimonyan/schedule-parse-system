package app.repository.dao;

import app.repository.models.dto.directory.Teacher;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TeacherRepository extends JpaRepository<Long, Teacher> {

  Optional<Teacher> findById(Long id);

}
