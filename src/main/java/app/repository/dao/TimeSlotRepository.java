package app.repository.dao;

import app.repository.models.entity.TimeSlot;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TimeSlotRepository extends JpaRepository<TimeSlot, Long> {

    /** Слот по естественному ключу — тому же, по которому его подбирает разбор файла. */
    Optional<TimeSlot> findByVersionIdAndDayWeekAndWeekCountAndLessonCountAndTimeRange(
            Long versionId, String dayWeek, Integer weekCount, Integer lessonCount, String timeRange);

    @Modifying
    @Transactional
    @Query("DELETE FROM TimeSlot s WHERE s.version.id = :versionId")
    void deleteByVersion(@Param("versionId") Long versionId);

    @Query("SELECT s FROM TimeSlot s WHERE s.version.id = :versionId AND s.isDeleted = false ORDER BY s.dayWeek, s.weekCount, s.lessonCount")
    List<TimeSlot> findAllByVersion(@Param("versionId") Long versionId);

    /**
     * Сетка версии целиком — разбор файла держит её в памяти и подбирает слот к паре, вместо
     * того чтобы спрашивать базу на каждую строку файла.
     */
    @Query("SELECT s FROM TimeSlot s WHERE s.version.id = :versionId")
    List<TimeSlot> findGrid(@Param("versionId") Long versionId);

}
