package app.repository.dao;

import app.repository.models.entity.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface QuizRepository extends JpaRepository<Quiz, Long> {

    List<Quiz> findAllByPublishedTrueOrderByIdDesc();

    List<Quiz> findAllByOrderByIdDesc();

    /** Для экспорта: в порядке создания, чтобы импорт повторил тот же порядок */
    List<Quiz> findAllByOrderByIdAsc();

    List<Quiz> findAllByIdInOrderByIdAsc(Collection<Long> ids);

}
