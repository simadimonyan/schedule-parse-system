package app.repository.models.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;

@Data
@Entity
@Table(name = "schedule_table")
public class Schedule implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "schedule_id")
    private Long id;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "teacher_id")
    private Teacher teacher;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "group_id")
    private Group group;

    @Column(name = "lesson_count")
    private Integer lessonCount;

    @Column(name = "lesson_type")
    private String lessonType;

    @Column(name = "lesson_name")
    private String lessonName;

    @Column(name = "auditory")
    private String auditory;

    @Column(name = "day_week")
    private String dayWeek;

    @Column(name = "week_count")
    private Integer weekCount;

    @Column(name = "time_period")
    private String timePeriod;

}
