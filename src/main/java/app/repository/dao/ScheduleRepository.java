package app.repository.dao;

import app.repository.models.Group;
import app.repository.models.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    Optional<Schedule> findAllByGroupAndDayWeekAndWeekCount(Group group, String dayWeek, Integer weekCount);

}
