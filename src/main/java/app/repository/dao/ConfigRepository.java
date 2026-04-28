package app.repository.dao;

import app.repository.models.entity.Config;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ConfigRepository extends JpaRepository<Config, Long> {

    Optional<Config> findAllByKey(String key);

    Optional<List<Config>> findAllByKeyAndTag(String key, String tag);

    Optional<List<Config>> findAllByTag(String tag);

}
