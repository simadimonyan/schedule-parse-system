package app.repository.models.entity;

import app.repository.models.dto.directory.Group;
import app.repository.models.dto.directory.Teacher;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.io.Serializable;
import java.util.List;

/**
 * Пара расписания.
 *
 * <p>Группа и преподаватель хранятся идентификаторами мастер-сервиса, а не ссылками на
 * локальные таблицы: справочник принадлежит мастеру, и держать его копию у себя значит
 * поддерживать две версии одних и тех же данных. Названия для выдачи и поиска берутся из
 * общего кеша мастера в Redis, промахи — запросом к самому мастеру.
 *
 * <p>Предмет и аудитория остаются строками: в расписании это значение ячейки файла, а не
 * ссылка. В справочник мастера они уходят отдельно, при разборе.
 *
 * <p>Пара принадлежит {@link Version}: читатели видят только активную версию, разбор пишет в
 * черновик. День недели, чётность и время продублированы колонками рядом со ссылкой на
 * {@link #slot} — по ним идут все выборки расписания, и джойн ради трёх значений на каждой
 * строке недельной выдачи не окупается.
 */
@Data
@Entity
@Table(name = "schedule_table", indexes = {
        @Index(name = "idx_schedule_group_master", columnList = "group_master_id"),
        @Index(name = "idx_schedule_teacher_master", columnList = "teacher_master_id"),
        @Index(name = "idx_schedule_version", columnList = "version_id")
})
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"version", "slot"})
public class Schedule implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "schedule_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id")
    private Version version;

    /** Ячейка недельной сетки, в которой стоит пара. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id")
    private TimeSlot slot;


    @Column(name = "teacher_master_id")
    private Long teacherMasterId;

    @Column(name = "group_master_id")
    private Long groupMasterId;

    @Column(name = "lesson_count")
    private Integer lessonCount;

    @Column(name = "lesson_type")
    private String lessonType;

    @Column(name = "lesson_name")
    private String lessonName;

    @Column(name = "auditory")
    private String auditory;

    @Column(name = "day_week")
    private String dayWeek;

    // закрепленная дата
    @Column(name = "pinned_date")
    private String pinnedDate;

    @Column(name = "week_count")
    private Integer weekCount;

    @Column(name = "time_period")
    private String timePeriod;

    @Column(name = "eios")
    private String eiosLink;

    /**
     * Колонка объявлена целиком, вместе с умолчанием: таблица расписания уже существует и
     * не пуста, а {@code not null} без {@code default} Postgres на непустую таблицу не
     * добавит — обновление схемы упало бы, и пары перестали бы читаться вовсе.
     */
    @Column(name = "is_deleted", columnDefinition = "boolean not null default false")
    private Boolean isDeleted = false;

    /**
     * Группа парой, не колонкой таблицы: в базе от неё остаётся только
     * {@link #groupMasterId}.
     *
     * <p>Заполняется на обоих концах жизни пары. Парсер знает группу по названию из файла и
     * кладёт её сюда, а идентификатор справочника подставляется при сохранении — иначе
     * разбор файла пришлось бы перемежать походами в мастер-сервис. Чтение проделывает
     * обратное: поднимает группу из справочника по {@link #groupMasterId}, чтобы ответ API
     * нёс название, курс и уровень, а не голый идентификатор.
     */
    @Transient
    private Group group;

    /** Преподаватель пары — заполняется так же, как {@link #group}. */
    @Transient
    private Teacher teacher;

    /**
     * Изменения этой пары: переносы, отмены, замены.
     *
     * <p>Не колонка, а достроенное при чтении: изменение ссылается на пару, а не наоборот.
     * Ссылка на паре удержала бы ровно одно изменение, а пара повторяется каждую неделю и
     * собирает их по одному на дату — перенос на 14 марта и отмену 21-го одновременно.
     *
     * <p>Отменённая пара из выдачи не исчезает: клиенту нужно нарисовать её зачёркнутой, а не
     * не нарисовать вовсе.
     */
    @Transient
    private List<Change> changes;

}
