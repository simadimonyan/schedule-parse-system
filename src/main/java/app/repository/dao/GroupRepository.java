package app.repository.dao;

import app.repository.models.Group;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GroupRepository extends JpaRepository<Group, Long> {

    Optional<Group> findAllByName(String name);

    Optional<Group> findAllByCourse(Integer course);

    Optional<Group> findAllByCourseAndLevel(Integer course, String level);

}
