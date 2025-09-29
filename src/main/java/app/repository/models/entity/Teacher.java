package app.repository.models.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@Entity
@Table(name = "teacher_table")
public class Teacher implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "teacher_id")
    private Long id;

    @OneToMany(mappedBy = "teacher", cascade = CascadeType.ALL)
    private List<Schedule> schedule;

    // кафедра
    @Column(name = "department")
    private String department;

    @Column(name = "label")
    private String label;

}
