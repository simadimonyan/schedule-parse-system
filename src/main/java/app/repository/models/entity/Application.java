package app.repository.models.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.io.Serializable;

/**
 * Таблица application_table используется для
 * хранения ответов на вопросы: выбранный вариант (answer)
 * или значение (value) — текст либо оценка
 */
@Data
@Entity
@Table(name = "application_table")
public class Application implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "application_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Submission submission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Question question;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answer_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Answer answer;

    @Column(name = "value", columnDefinition = "text")
    private String value;

}
