package app.repository.models.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@Entity
@Table(name = "group_table")
public class Group implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "group_id")
    private Long id;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL)
    private List<Schedule> schedule;

    @Column(name = "course")
    private Integer course;

    // уровень образования
    @Column(name = "level")
    private String level;

    // форма обучения
    @Column(name = "studyForm")
    private String studyForm;

    @Column(name = "name")
    private String name;

    @Column(name = "updated_at")
    private Long updatedAt;

}
