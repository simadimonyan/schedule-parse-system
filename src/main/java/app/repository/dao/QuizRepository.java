package app.repository.dao;

import app.repository.models.entity.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuizRepository extends JpaRepository<Quiz, Long> {

    List<Quiz> findAllByPublishedTrueOrderByIdDesc();

    List<Quiz> findAllByOrderByIdDesc();

}
