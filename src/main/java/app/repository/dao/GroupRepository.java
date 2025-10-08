package app.repository.dao;

import app.repository.models.entity.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface GroupRepository extends JpaRepository<Group, Long> {

    Optional<Group> findByName(String name);

    Optional<List<Group>> findAllByCourse(Integer course);

    Optional<List<Group>> findAllByCourseAndLevel(Integer course, String level);

    @Query("SELECT DISTINCT g.level FROM Group g WHERE g.course = :course")
    Optional<List<String>> findDistinctLevels(Integer course);

    @Query("SELECT DISTINCT g.course FROM Group g")
    Optional<List<Integer>> findDistinctCourses();

}
