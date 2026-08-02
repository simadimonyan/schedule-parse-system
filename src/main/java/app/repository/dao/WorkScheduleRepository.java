package app.repository.dao;

import app.repository.models.entity.WorkSchedule;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface WorkScheduleRepository extends JpaRepository<WorkSchedule, Long> {

    /** Весь график версии, включая мягко удалённый — читают копирование и чистка. */
    @Query("SELECT w FROM WorkSchedule w WHERE w.version.id = :versionId")
    List<WorkSchedule> findAllByVersion(@Param("versionId") Long versionId);

    @Modifying
    @Transactional
    @Query("DELETE FROM WorkSchedule w WHERE w.version.id = :versionId")
    void deleteByVersion(@Param("versionId") Long versionId);

    @Query("""
            SELECT w FROM WorkSchedule w
            WHERE w.version.id = :versionId AND w.teacherMasterId = :teacherMasterId AND w.isDeleted = false
            ORDER BY w.dayWeek, w.startedAt
            """)
    List<WorkSchedule> findAllByTeacher(
            @Param("versionId") Long versionId,
            @Param("teacherMasterId") Long teacherMasterId);

    @Query("""
            SELECT w FROM WorkSchedule w
            WHERE w.version.id = :versionId AND w.teacherMasterId IN :teacherMasterIds AND w.isDeleted = false
            """)
    List<WorkSchedule> findAllByTeachers(
            @Param("versionId") Long versionId,
            @Param("teacherMasterIds") Collection<Long> teacherMasterIds);

}
