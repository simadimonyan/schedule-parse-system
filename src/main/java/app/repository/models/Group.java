package app.repository.models;

import jakarta.persistence.*;
import lombok.Data;

import java.util.List;

@Data
@Entity
@Table(name = "group_table")
public class Group {

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

    @Column(name = "name")
    private String name;

}
