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
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalTime;

/**
 * Ячейка недельной сетки: номер пары, день недели, чётность и время звонков.
 *
 * <p>На схеме таблица называется «Временной слот», но её ключ подписан {@code period_id}.
 * Здесь он {@code slot_id} намеренно: рядом появляется учебный период — практика, сессия,
 * каникулы, — и два разных смысла под одним именем в соседних таблицах читаются
 * неоднозначно. Слот отвечает на вопрос «когда внутри недели», период — «в каком отрезке
 * календаря».
 *
 * <p>Естественный ключ — версия вместе с днём, чётностью, номером пары и временем: разбор
 * файла переиспользует уже заведённый слот, а не плодит копию на каждую пару.
 * {@link #timeRange} хранится строкой, как приходит из файла («8:00-9:30»); разобранные
 * границы лежат рядом и пустуют, если формат ячейки оказался незнакомым.
 */
@Data
@Entity
@Table(
        name = "slot_table",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_slot_natural_key",
                columnNames = {"version_id", "day_week", "week_count", "lesson_count", "time_range"}
        ),
        indexes = @Index(name = "idx_slot_version", columnList = "version_id")
)
@EqualsAndHashCode(of = "id")
@ToString(exclude = "version")
public class TimeSlot implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "slot_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id")
    private Version version;

    /** Номер пары внутри дня. */
    @Column(name = "lesson_count")
    private Integer lessonCount;

    /** Чётность недели: 1 или 2. */
    @Column(name = "week_count")
    private Integer weekCount;

    @Column(name = "day_week")
    private String dayWeek;

    /** Время пары строкой из файла — оно же часть естественного ключа слота. */
    @Column(name = "time_range")
    private String timeRange;

    @Column(name = "started_at")
    private LocalTime startedAt;

    @Column(name = "finished_at")
    private LocalTime finishedAt;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

}
