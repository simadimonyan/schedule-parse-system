package app.repository.dao;

import app.repository.models.entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    boolean existsByQuizIdAndRespondentHash(Long quizId, String respondentHash);

    long countByQuizId(Long quizId);

    long countByCreatedAtAfter(LocalDateTime from);

    @Query("SELECT s.quiz.id, COUNT(s) FROM Submission s WHERE s.quiz.id IN :quizIds GROUP BY s.quiz.id")
    List<Object[]> countByQuizIds(Collection<Long> quizIds);

    @Query("SELECT s.quiz.id FROM Submission s WHERE s.respondentHash IN :hashes")
    List<Long> findQuizIdsByRespondentHashes(Collection<String> hashes);

    @Modifying
    @Query("DELETE FROM Submission s WHERE s.quiz.id = :quizId")
    void deleteByQuizId(Long quizId);

}
