package app.repository.models;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "schedule_table")
public class Schedule {

    @Id
    @GeneratedValue
    @Column(name = "schedule_id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "teacher_id")
    private Teacher teacher;

    @ManyToOne
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
