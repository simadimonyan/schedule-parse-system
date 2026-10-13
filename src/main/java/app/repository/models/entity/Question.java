package app.repository.models.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Таблица question_table используется для
 * хранения вопросов опроса
 */
@Data
@Entity
@Table(name = "question_table")
public class Question implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "question_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Quiz quiz;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Answer> answers = new ArrayList<>();

    @Column(name = "position")
    private Integer position;

    @Column(name = "label", columnDefinition = "text")
    private String label;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private QuestionType type;

    @Column(name = "required")
    private Boolean required;

    /** Верхняя граница шкалы для вопросов RATING */
    @Column(name = "scale")
    private Integer scale;

}
