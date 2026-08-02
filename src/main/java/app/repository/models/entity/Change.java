package app.repository.models.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * Изменение расписания: перенос, отмена, замена или разовая пара.
 *
 * <p>Справочные сущности заданы идентификаторами мастер-сервиса — как и в {@link Schedule},
 * своей копии справочника у расписания нет. Заполнены не все: у отмены нет ни аудитории, ни
 * замены, у переноса меняется только слот. Пустое поле означает «не менялось», а не «не
 * знаем».
 *
 * <p>{@link #payload} — то, что не разложилось по колонкам: комментарий деканата, номер
 * приказа, исходные значения до замены. Класть туда данные, по которым нужен поиск, не
 * стоит: индексов на них нет.
 */
@Data
@Entity
@Table(name = "change_table", indexes = {
        @Index(name = "idx_change_version", columnList = "version_id"),
        @Index(name = "idx_change_group_master", columnList = "group_master_id"),
        @Index(name = "idx_change_teacher_master", columnList = "teacher_master_id"),
        @Index(name = "idx_change_date", columnList = "change_date"),
        @Index(name = "idx_change_schedule", columnList = "schedule_id")
})
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"version", "slot", "schedule"})
public class Change implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "change_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id")
    private Version version;

    /** Слот, которого касается изменение; у разовой пары — слот, куда она встала. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id")
    private TimeSlot slot;

    /**
     * Пара, к которой относится изменение.
     *
     * <p>Ссылка стоит здесь, а не на паре, как нарисовано на схеме: пара повторяется каждую
     * неделю, а изменения точечные, и одной колонки на паре хватило бы ровно на одно. Перенос
     * 14 марта и отмена 21-го — это два изменения одной и той же пары.
     *
     * <p>Пусто у изменений, которым пары нет: разовая пара, которой не было в расписании, или
     * общее распоряжение по группе. Такие уходят в выдачу отдельным списком.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id")
    private Schedule schedule;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false)
    private ChangeType changeType;

    /** День, на который действует изменение: изменения всегда точечные, не «каждую неделю». */
    @Column(name = "change_date")
    private LocalDate changeDate;

    @Column(name = "subject_master_id")
    private Long subjectMasterId;

    @Column(name = "teacher_master_id")
    private Long teacherMasterId;

    @Column(name = "group_master_id")
    private Long groupMasterId;

    @Column(name = "auditorium_master_id")
    private Long auditoriumMasterId;

    @Column(name = "department_master_id")
    private Long departmentMasterId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload")
    private Map<String, Object> payload;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

}
