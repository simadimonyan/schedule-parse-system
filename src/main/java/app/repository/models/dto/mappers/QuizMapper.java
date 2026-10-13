package app.repository.models.dto.mappers;

import app.repository.models.dto.api.quiz.response.QuizResponse;
import app.repository.models.dto.api.quiz.response.QuizzesResponse;
import app.repository.models.dto.api.quiz.unit.AnswerUnit;
import app.repository.models.dto.api.quiz.unit.QuestionUnit;
import app.repository.models.entity.Answer;
import app.repository.models.entity.Question;
import app.repository.models.entity.Quiz;
import app.repository.models.entity.QuizStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class QuizMapper {

    public QuizResponse toQuizResponse(Quiz quiz, Long applications) {
        List<QuestionUnit> questions = new ArrayList<>();
        for (Question q : quiz.getQuestions()) {
            List<AnswerUnit> answers = new ArrayList<>();
            for (Answer a : q.getAnswers()) {
                answers.add(new AnswerUnit(a.getId(), a.getLabel()));
            }
            questions.add(new QuestionUnit(q.getId(), q.getLabel(), q.getDescription(), q.getType().name(), q.getRequired(), q.getScale(), answers));
        }
        return new QuizResponse(
            quiz.getId(),
            quiz.getName(),
            quiz.getDescription(),
            quiz.getCompletionMessage(),
            QuizStatus.of(quiz, QuizStatus.now()).label(),
            quiz.getGroups().stream().sorted().toList(),
            questions,
            format(quiz.getStartDate()),
            format(quiz.getEndDate()),
            applications
        );
    }

    public QuizzesResponse toQuizzesResponse(List<Quiz> quizzes, Map<Long, Long> applications) {
        List<QuizResponse> units = new ArrayList<>();
        for (Quiz quiz : quizzes) {
            units.add(toQuizResponse(quiz, applications.getOrDefault(quiz.getId(), 0L)));
        }
        return new QuizzesResponse(units);
    }

    private String format(LocalDateTime date) {
        return date == null ? null : date.toString();
    }

}
