package app.repository.dao;

import app.repository.models.dto.directory.Group;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GroupRepository extends JpaRepository<Long, Group> {

  Optional<Group> findById(Long group);

}
