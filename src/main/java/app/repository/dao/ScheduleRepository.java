package app.repository.dao;

import app.repository.models.entity.Schedule;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    Optional<List<Schedule>> findAllByGroupNameAndWeekCount(String groupName, Integer weekCount);

    Optional<List<Schedule>> findAllByGroupNameAndDayWeekAndWeekCount(String groupName, String dayWeek, Integer weekCount);

    Optional<List<Schedule>> findAllByTeacherLabelAndWeekCount(String groupName, Integer weekCount);

    Optional<List<Schedule>> findAllByTeacherLabelAndDayWeekAndWeekCount(String groupName, String dayWeek, Integer weekCount);

    @Modifying
    @Transactional
    @Query("DELETE FROM Schedule s WHERE s.group.id = :groupId")
    void deleteAllByGroupId(@Param("groupId") Long groupId);

}
