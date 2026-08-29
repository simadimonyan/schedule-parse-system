package app.repository.models.dto.api.schedule;

import app.repository.models.dto.directory.Group;
import app.repository.models.dto.directory.Teacher;
import app.repository.models.entity.Version;
import java.util.List;

public record ScheduleBatch(List<ScheduleUnitRequest> schedule) {

  public record ScheduleUnitRequest(
      Long version,
      Long group,
      String dayWeek,
      String timePeriod,
      Integer weekCount,
      Integer lessonCount,
      String lessonType,
      String lessonName,
      Long teacher,
      String pinnedDate,
      String auditory,
      String eiosLink
  ) {}

  /**
   * Пара с уже разрешёнными справочником группой, преподавателем и версией.
   *
   * <p>Место в недельной сетке едет полями, а не ссылкой на слот: слота может ещё не быть —
   * группу заводят и до первой загрузки файла, — и подбирает его уже сохранение, тем же
   * ключом, что и разбор файла.
   */
  public record ScheduleBatchUnit(
      Version version,
      Group group,
      Long groupMasterId,
      String dayWeek,
      String timePeriod,
      Integer weekCount,
      Integer lessonCount,
      String lessonType,
      String lessonName,
      Teacher teacher,
      Long teacherMasterId,
      String pinnedDate,
      String auditory,
      String eiosLink
  ) {}

}
