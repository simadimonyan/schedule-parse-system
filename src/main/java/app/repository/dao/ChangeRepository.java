package app.repository.dao;

import app.repository.models.entity.Change;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface ChangeRepository extends JpaRepository<Change, Long> {

    /** Все изменения версии, включая мягко удалённые — читают копирование и чистка. */
    @Query("SELECT c FROM Change c WHERE c.version.id = :versionId")
    List<Change> findAllByVersion(@Param("versionId") Long versionId);

    @Modifying
    @Transactional
    @Query("DELETE FROM Change c WHERE c.version.id = :versionId")
    void deleteByVersion(@Param("versionId") Long versionId);

    @Query("""
            SELECT c FROM Change c
            WHERE c.version.id = :versionId AND c.groupMasterId = :groupMasterId AND c.isDeleted = false
            ORDER BY c.changeDate
            """)
    List<Change> findAllByGroup(@Param("versionId") Long versionId, @Param("groupMasterId") Long groupMasterId);

    @Query("""
            SELECT c FROM Change c
            WHERE c.version.id = :versionId AND c.teacherMasterId = :teacherMasterId AND c.isDeleted = false
            ORDER BY c.changeDate
            """)
    List<Change> findAllByTeacher(@Param("versionId") Long versionId, @Param("teacherMasterId") Long teacherMasterId);

    /**
     * Изменения выданных пар — одним запросом на всю недельную выдачу.
     *
     * <p>Поштучно спрашивать нельзя: в неделе группы под сорок пар, и это были бы сорок
     * походов в базу ради выдачи, которая до сих пор обходилась одним.
     */
    @Query("""
            SELECT c FROM Change c
            WHERE c.schedule.id IN :scheduleIds AND c.isDeleted = false
            ORDER BY c.changeDate
            """)
    List<Change> findAllBySchedules(@Param("scheduleIds") Collection<Long> scheduleIds);

    /**
     * Изменения группы, не привязанные ни к одной паре.
     *
     * <p>Это разовые пары, которых в расписании не было, и общие распоряжения. Привязать их
     * не к чему, но показать нужно — иначе добавленное занятие не попадёт в выдачу вовсе.
     */
    @Query("""
            SELECT c FROM Change c
            WHERE c.version.id = :versionId AND c.groupMasterId = :groupMasterId
              AND c.schedule IS NULL AND c.isDeleted = false
            ORDER BY c.changeDate
            """)
    List<Change> findStandaloneByGroup(
            @Param("versionId") Long versionId,
            @Param("groupMasterId") Long groupMasterId);

    /** Изменения преподавателя, не привязанные ни к одной паре. */
    @Query("""
            SELECT c FROM Change c
            WHERE c.version.id = :versionId AND c.teacherMasterId = :teacherMasterId
              AND c.schedule IS NULL AND c.isDeleted = false
            ORDER BY c.changeDate
            """)
    List<Change> findStandaloneByTeacher(
            @Param("versionId") Long versionId,
            @Param("teacherMasterId") Long teacherMasterId);

    /**
     * Отвязывает изменения от пар, которые заменяет новая загрузка файла.
     *
     * <p>Пары пересоздаются с новыми идентификаторами, и ссылка всё равно оборвалась бы — а
     * внешний ключ не дал бы удалить старые строки. Само изменение при этом остаётся: приказ
     * деканата не отменяется тем, что расписание перезалили. Оно уходит в выдачу отдельным
     * списком, и привязать его заново — дело администратора.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Change c SET c.schedule = null WHERE c.schedule.id IN (" +
            "SELECT s.id FROM Schedule s WHERE s.version.id = :versionId AND s.groupMasterId IN :groupMasterIds)")
    int detachFromGroups(
            @Param("versionId") Long versionId,
            @Param("groupMasterIds") Collection<Long> groupMasterIds);

    @Query("""
            SELECT c FROM Change c
            WHERE c.version.id = :versionId AND c.changeDate BETWEEN :from AND :to AND c.isDeleted = false
            ORDER BY c.changeDate
            """)
    List<Change> findAllByPeriod(
            @Param("versionId") Long versionId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

}
