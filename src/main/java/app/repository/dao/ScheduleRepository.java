package app.repository.dao;

import app.repository.models.entity.Group;
import app.repository.models.entity.Schedule;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    Optional<Schedule> findAllByGroupAndDayWeekAndWeekCount(Group group, String dayWeek, Integer weekCount);

    @Modifying
    @Transactional
    @Query("DELETE FROM Schedule s WHERE s.group.id = :groupId")
    void deleteAllByGroupId(@Param("groupId") Long groupId);

}
