package app.repository.models.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalTime;

/**
 * График занятости преподавателя: в какие часы какого дня он готов вести пары.
 *
 * <p>Ограничение для раскладки, а не факт расписания: строки говорят, куда пару ставить
 * можно, а сами пары лежат в {@link Schedule}. Преподаватель задан идентификатором
 * справочника мастер-сервиса — своей таблицы у него в расписании нет.
 *
 * <p>Границы — время суток, а не метка времени, как подписано на схеме: строка отвечает за
 * повторяющийся день недели, и календарная дата в ней смысла не имеет.
 */
@Data
@Entity
@Table(name = "work_schedule_table", indexes = {
        @Index(name = "idx_work_schedule_version", columnList = "version_id"),
        @Index(name = "idx_work_schedule_teacher_master", columnList = "teacher_master_id")
})
@EqualsAndHashCode(of = "id")
@ToString(exclude = "version")
public class WorkSchedule implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "work_schedule_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id")
    private Version version;

    @Column(name = "teacher_master_id")
    private Long teacherMasterId;

    @Column(name = "day_week")
    private String dayWeek;

    @Column(name = "started_at")
    private LocalTime startedAt;

    @Column(name = "finished_at")
    private LocalTime finishedAt;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

}
