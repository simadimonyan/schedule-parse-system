package app.repository.models.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "config_table")
public class Config {

    @Id
    @GeneratedValue
    @Column(name = "config_id")
    private Long id;

    @Column(name = "key")
    private String key;

    @Column(name = "value")
    private String value;

}
