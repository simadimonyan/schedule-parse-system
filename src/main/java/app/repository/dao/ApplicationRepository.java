package app.repository.dao;

import app.repository.models.entity.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    /** Все ответы опроса для подсчёта итогов, свежие анкеты первыми */
    @Query("""
        SELECT a FROM Application a
        JOIN FETCH a.submission s
        LEFT JOIN FETCH a.answer
        WHERE s.quiz.id = :quizId
        ORDER BY s.createdAt DESC
        """)
    List<Application> findAllForResults(Long quizId);

    @Modifying
    @Query("DELETE FROM Application a WHERE a.submission.id IN (SELECT s.id FROM Submission s WHERE s.quiz.id = :quizId)")
    void deleteByQuizId(Long quizId);

}
