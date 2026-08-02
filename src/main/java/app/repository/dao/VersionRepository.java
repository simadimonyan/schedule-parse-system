package app.repository.dao;

import app.repository.models.entity.Version;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * Хранилище версий расписания.
 *
 * <p>Активная и черновая версии ищутся списком, а не одной записью: флаги ничем не
 * ограничены на уровне базы, и вторая активная строка должна оказаться странными данными в
 * логе, а не {@code NonUniqueResultException} на каждом чтении расписания.
 */
public interface VersionRepository extends JpaRepository<Version, Long> {

    @Query("SELECT v FROM Version v WHERE v.isActive = true AND v.isDeleted = false ORDER BY v.id DESC")
    List<Version> findActive();

    @Query("SELECT v FROM Version v WHERE v.isDraft = true AND v.isDeleted = false ORDER BY v.id DESC")
    List<Version> findDrafts();

    @Query("SELECT v FROM Version v WHERE v.isDeleted = false ORDER BY v.id DESC")
    List<Version> findAllAlive();

    Optional<Version> findByIdAndIsDeletedFalse(Long id);

}
