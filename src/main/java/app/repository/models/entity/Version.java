package app.repository.models.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

import java.io.Serializable;
import java.time.Instant;

/**
 * Версия расписания — снимок всего, что сервис хранит на один момент времени.
 *
 * <p>Корень схемы: пары, слоты, изменения, график занятости и метрики принадлежат версии, и
 * ни одна строка не живёт сама по себе. Смысл в том, чтобы правку можно было собрать
 * целиком, посмотреть и только потом отдать читателям — либо откатить, не разбирая по
 * строкам, что именно приехало последней загрузкой.
 *
 * <p>Два флага вместо одного статуса, как на схеме. {@link #isActive} — версия, которую
 * видят клиенты; ровно одна на сервис, переключается {@code VersionService}. {@link #isDraft}
 * — версия, в которую идёт запись при разборе файлов. Обычно это разные строки: пока
 * черновик наполняется, читатели работают с прошлой активной версией.
 */
@Data
@Entity
@Table(name = "version_table", indexes = {
        @Index(name = "idx_version_active", columnList = "is_active"),
        @Index(name = "idx_version_draft", columnList = "is_draft")
})
public class Version implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "version_id")
    private Long id;

    @Column(name = "name")
    private String name;

    /** Версия, которую отдают клиентам. Активной остаётся одна — переключение в сервисе. */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = false;

    /** Версия, открытая на запись: в неё пишет разбор файлов до публикации. */
    @Column(name = "is_draft", nullable = false)
    private Boolean isDraft = false;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

}
