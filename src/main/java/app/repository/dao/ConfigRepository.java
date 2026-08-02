package app.repository.dao;

import app.repository.models.entity.Config;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConfigRepository extends JpaRepository<Config, Long> {

    Optional<Config> findAllByKey(String key);

    Optional<List<Config>> findAllByKeyAndTag(String key, String tag);

    Optional<List<Config>> findAllByTag(String tag);

    /**
     * Настройки, привязанные к версии.
     *
     * <p>Общесервисные значения вроде чётности недели сюда не попадают: у них версии нет, и
     * при копировании черновика они остаются на месте — переключение чётности относится ко
     * всему сервису, а не к конкретному снимку расписания.
     */
    @Query("SELECT c FROM Config c WHERE c.version.id = :versionId")
    List<Config> findAllByVersion(@Param("versionId") Long versionId);

    @Modifying
    @Transactional
    @Query("DELETE FROM Config c WHERE c.version.id = :versionId")
    void deleteByVersion(@Param("versionId") Long versionId);

}
