package app.repository.models.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Таблица quiz_table используется для хранения опросов.
 * Опрос виден клиентам, только когда опубликован, и принимает ответы
 * в промежутке [startDate, endDate] (пустая граница — без ограничения)
 */
@Data
@Entity
@Table(name = "quiz_table")
public class Quiz implements Serializable {

    public static final String MONTHLY = "monthly";

    @Id
    @GeneratedValue
    @Column(name = "quiz_id")
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    /** Сообщение, которое респондент видит после отправки ответов */
    @Column(name = "completion_message", columnDefinition = "text")
    private String completionMessage;

    @Column(name = "published")
    private Boolean published;

    @Column(name = "start_date")
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    /**
     * Повтор опроса: null — пройти можно один раз, {@link #MONTHLY} — раз в каждом месяце.
     * Колонка nullable, старые опросы остаются одноразовыми
     */
    @Column(name = "recurrence")
    private String recurrence;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Группы, для которых проводится опрос; пусто — для всех */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "quiz_group_table", joinColumns = @JoinColumn(name = "quiz_id"))
    @Column(name = "group_name")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<String> groups = new HashSet<>();

    @OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Question> questions = new ArrayList<>();

}
