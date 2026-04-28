package app.repository.models.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;

@Data
@Entity
@Table(name = "config_table")
public class Config implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "config_id")
    private Long id;

    @Column(name = "tag")
    private String tag;

    @Column(name = "key")
    private String key;

    @Column(name = "value")
    private String value;

    @Column(name = "created_at")
    private String createdAt;

    @Column(name = "updated_at")
    private String updatedAt;

}
