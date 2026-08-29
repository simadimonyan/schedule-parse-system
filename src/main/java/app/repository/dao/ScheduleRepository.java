package app.repository.dao;

import app.repository.models.entity.Schedule;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * Хранилище пар расписания.
 *
 * <p>Группа и преподаватель задаются идентификатором мастер-сервиса: названия в таблице
 * больше нет, и запросам по имени взяться неоткуда. Имя из запроса клиента переводится в
 * идентификатор выше — в {@code MasterDirectoryService}, по локальному индексу связей, — а
 * сюда приходит уже готовый {@code master_id}.
 *
 * <p>Каждая выборка ограничена версией. Без неё чтение вернуло бы пары всех снимков сразу:
 * старые версии из таблицы не удаляются, в этом и смысл — на них откатываются.
 *
 * <p>День, чётность, номер пары и время лежат в слоте, поэтому отбор по ним идёт джойном, а
 * слот подтягивается тем же запросом: выдача сразу за ним и пойдёт, и без {@code fetch} на
 * каждую пару недели ушёл бы отдельный select. Джойн внутренний — пара без слота не стоит
 * нигде в сетке, и показывать её негде.
 */
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    @Query("""
            SELECT s FROM Schedule s JOIN FETCH s.slot slot
            WHERE s.version.id = :versionId AND s.groupMasterId = :groupMasterId
              AND slot.weekCount = :weekCount AND s.isDeleted = false
            """)
    List<Schedule> findAllByGroup(
            @Param("versionId") Long versionId,
            @Param("groupMasterId") Long groupMasterId,
            @Param("weekCount") Integer weekCount);

    @Query("""
            SELECT s FROM Schedule s JOIN FETCH s.slot slot
            WHERE s.version.id = :versionId AND s.groupMasterId = :groupMasterId
              AND slot.dayWeek = :dayWeek AND slot.weekCount = :weekCount AND s.isDeleted = false
            """)
    List<Schedule> findAllByGroupAndDay(
            @Param("versionId") Long versionId,
            @Param("groupMasterId") Long groupMasterId,
            @Param("dayWeek") String dayWeek,
            @Param("weekCount") Integer weekCount);

    @Query("""
            SELECT s FROM Schedule s JOIN FETCH s.slot slot
            WHERE s.version.id = :versionId AND s.teacherMasterId = :teacherMasterId
              AND slot.weekCount = :weekCount AND s.isDeleted = false
            """)
    List<Schedule> findAllByTeacher(
            @Param("versionId") Long versionId,
            @Param("teacherMasterId") Long teacherMasterId,
            @Param("weekCount") Integer weekCount);

    @Query("""
            SELECT s FROM Schedule s JOIN FETCH s.slot slot
            WHERE s.version.id = :versionId AND s.teacherMasterId = :teacherMasterId
              AND slot.dayWeek = :dayWeek AND slot.weekCount = :weekCount AND s.isDeleted = false
            """)
    List<Schedule> findAllByTeacherAndDay(
            @Param("versionId") Long versionId,
            @Param("teacherMasterId") Long teacherMasterId,
            @Param("dayWeek") String dayWeek,
            @Param("weekCount") Integer weekCount);

    /**
     * Все пары версии — читает копирование при заведении черновика.
     *
     * <p>Джойн левый, в отличие от выдачи: копия обязана повторить снимок как есть, включая
     * пары, которым слот ещё не подобран, — иначе черновик молча терял бы строки.
     */
    @Query("SELECT s FROM Schedule s LEFT JOIN FETCH s.slot WHERE s.version.id = :versionId")
    List<Schedule> findAllByVersion(@Param("versionId") Long versionId);

    /**
     * Сносит пары групп внутри одной версии.
     *
     * <p>Ограничение по версии обязательно: файл заменяет расписание своих групп в черновике,
     * а не во всех снимках сразу, иначе загрузка нового расписания стирала бы то, ради чего
     * версии и заведены — состояние, на которое можно откатиться.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM Schedule s WHERE s.version.id = :versionId AND s.groupMasterId IN :groupMasterIds")
    void deleteByVersionAndGroupMasterIdIn(
            @Param("versionId") Long versionId,
            @Param("groupMasterIds") Collection<Long> groupMasterIds);

    /**
     * Сносит пары одной ячейки: группа в своём слоте внутри версии.
     *
     * <p>Мельче, чем замена по группе, и в этом весь смысл. Файл приносит расписание группы
     * целиком, а редактор — одну пару: диспетчер ставит её в клетку сетки и ждёт, что
     * остальная неделя останется на месте. Замена по группе стёрла бы её всю.
     *
     * <p>Подгруппы при этом уживаются: ячейка чистится один раз на всю пачку, и обе пары,
     * пришедшие в неё вместе, встают рядом.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM Schedule s WHERE s.version.id = :versionId "
            + "AND s.groupMasterId = :groupMasterId AND s.slot.id = :slotId")
    int deleteCell(
            @Param("versionId") Long versionId,
            @Param("groupMasterId") Long groupMasterId,
            @Param("slotId") Long slotId);

    /** Все пары версии — используется при удалении черновика. */
    @Modifying
    @Transactional
    @Query("DELETE FROM Schedule s WHERE s.version.id = :versionId")
    void deleteByVersion(@Param("versionId") Long versionId);

    /**
     * Группы, у которых есть расписание.
     *
     * <p>Заменяет таблицу-реплику в выдаче списков: перечень групп сервиса — это ровно те,
     * чьи пары он хранит. Название, курс и уровень к идентификаторам добавляет справочник.
     */
    @Query("""
            SELECT DISTINCT s.groupMasterId FROM Schedule s
            WHERE s.version.id = :versionId AND s.groupMasterId IS NOT NULL AND s.isDeleted = false
            """)
    List<Long> findDistinctGroupMasterIds(@Param("versionId") Long versionId);

    /** Преподаватели, у которых есть расписание. */
    @Query("""
            SELECT DISTINCT s.teacherMasterId FROM Schedule s
            WHERE s.version.id = :versionId AND s.teacherMasterId IS NOT NULL AND s.isDeleted = false
            """)
    List<Long> findDistinctTeacherMasterIds(@Param("versionId") Long versionId);

    /**
     * Пары «предмет — тип занятия» для предметов, у которых тип единственный во всём
     * расписании версии.
     *
     * <p>Считать по одному файлу нельзя: предмет, который в файле очной формы идёт только
     * лекцией, в файле заочной может оказаться только практикой, и значение в справочнике
     * прыгало бы вслед за последней загрузкой. Однозначность определяется по всем
     * разобранным файлам версии сразу.
     */
    @Query("""
            SELECT s.lessonName, MIN(s.lessonType) FROM Schedule s
            WHERE s.version.id = :versionId AND s.lessonName IN :names
              AND s.lessonType IS NOT NULL AND s.lessonType <> ''
            GROUP BY s.lessonName
            HAVING COUNT(DISTINCT s.lessonType) = 1
            """)
    List<Object[]> findUnambiguousLessonTypes(
            @Param("versionId") Long versionId,
            @Param("names") Collection<String> names);

    /** Сколько пар осталось без версии — их подбирает начальная миграция при первом старте. */
    @Query("SELECT count(s) FROM Schedule s WHERE s.version IS NULL")
    long countOrphans();

    /**
     * Отдаёт версии все пары, загруженные до появления версий.
     *
     * <p>Одним запросом, а не перебором сущностей: на рабочей базе это десятки тысяч строк, и
     * поднимать их в память ради проставления одной ссылки незачем.
     *
     * <p>{@code flushAutomatically} обязателен. Начальная версия заводится в той же
     * транзакции, а её id выдаёт последовательность — INSERT остаётся висеть в контексте
     * персистентности. Bulk-update идёт в БД мимо контекста и сам его не сбрасывает: таблицы
     * разные, автосброса по пересечению не случается. Без флага UPDATE проставляет ссылку на
     * строку version_table, которой в базе ещё нет, и падает по внешнему ключу.
     */
    @Modifying(flushAutomatically = true)
    @Transactional
    @Query("UPDATE Schedule s SET s.version.id = :versionId WHERE s.version IS NULL")
    int adopt(@Param("versionId") Long versionId);

}
