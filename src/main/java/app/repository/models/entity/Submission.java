package app.repository.models.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Таблица submission_table используется для хранения
 * заполненных анкет: одна строка — один респондент в одном опросе.
 * Сам идентификатор респондента не хранится, только его хеш —
 * его хватает, чтобы не принять анкету дважды.
 * У ежемесячного опроса в хеш входит и месяц — одна анкета в месяц
 */
@Data
@Entity
@Table(
    name = "submission_table",
    uniqueConstraints = @UniqueConstraint(name = "uq_submission_respondent", columnNames = {"quiz_id", "respondent_hash"})
)
public class Submission implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "submission_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Quiz quiz;

    @Column(name = "respondent_hash", length = 64)
    private String respondentHash;

    @Column(name = "group_name")
    private String group;

    /** Месяц анкеты "yyyy-MM" для ежемесячных опросов; у одноразовых — null */
    @Column(name = "period", length = 7)
    private String period;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Application> applications = new ArrayList<>();

}
