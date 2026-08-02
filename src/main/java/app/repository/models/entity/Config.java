package app.repository.models.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.io.Serializable;

/**
 * Пара «ключ — значение» с меткой: настройки сервиса и счётчики метрик из одной таблицы
 * (на схеме она подписана «Метрики»).
 *
 * <p>Версия проставляется, когда значение относится к конкретному снимку расписания, и
 * пустует у общесервисных настроек вроде чётности недели: та живёт поверх всех версий и при
 * публикации не переезжает.
 */
@Data
@Entity
@Table(name = "config_table", indexes = {
        @Index(name = "idx_config_version", columnList = "version_id"),
        @Index(name = "idx_config_key", columnList = "key")
})
@EqualsAndHashCode(of = "id")
@ToString(exclude = "version")
public class Config implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "config_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id")
    private Version version;

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
