package app.repository.models.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.io.Serializable;

/**
 * Таблица answer_table используется для
 * хранения вариантов ответа на вопросы с выбором
 */
@Data
@Entity
@Table(name = "answer_table")
public class Answer implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "answer_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Question question;

    @Column(name = "position")
    private Integer position;

    @Column(name = "label", columnDefinition = "text")
    private String label;

}
