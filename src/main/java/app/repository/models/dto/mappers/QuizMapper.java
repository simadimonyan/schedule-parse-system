package app.repository.models.dto.mappers;

import app.repository.models.dto.api.quiz.response.QuizExportResponse;
import app.repository.models.dto.api.quiz.response.QuizResponse;
import app.repository.models.dto.api.quiz.response.QuizzesResponse;
import app.repository.models.dto.api.quiz.unit.AnswerUnit;
import app.repository.models.dto.api.quiz.unit.OptionExportUnit;
import app.repository.models.dto.api.quiz.unit.QuestionExportUnit;
import app.repository.models.dto.api.quiz.unit.QuestionUnit;
import app.repository.models.dto.api.quiz.unit.QuizExportUnit;
import app.repository.models.entity.Answer;
import app.repository.models.entity.Question;
import app.repository.models.entity.Quiz;
import app.repository.models.entity.QuizStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class QuizMapper {

    /** Даты в документе экспорта — всегда с секундами, их понимает и импорт, и /publish */
    private static final DateTimeFormatter EXPORT_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

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
            applications,
            quiz.getRecurrence(),
            QuizStatus.period(quiz, QuizStatus.now())
        );
    }

    public QuizzesResponse toQuizzesResponse(List<Quiz> quizzes, Map<Long, Long> applications) {
        List<QuizResponse> units = new ArrayList<>();
        for (Quiz quiz : quizzes) {
            units.add(toQuizResponse(quiz, applications.getOrDefault(quiz.getId(), 0L)));
        }
        return new QuizzesResponse(units);
    }

    /** Документ экспорта: описание опросов без id и анкет, переносимое между стендами */
    public QuizExportResponse toExportResponse(List<Quiz> quizzes, LocalDateTime exportedAt) {
        List<QuizExportUnit> units = new ArrayList<>();
        for (Quiz quiz : quizzes) {
            List<QuestionExportUnit> questions = new ArrayList<>();
            for (Question q : quiz.getQuestions()) {
                List<OptionExportUnit> answers = new ArrayList<>();
                for (Answer a : q.getAnswers()) answers.add(new OptionExportUnit(a.getLabel()));
                questions.add(new QuestionExportUnit(q.getLabel(), q.getDescription(), q.getType().name(), q.getRequired(), q.getScale(), answers));
            }
            units.add(new QuizExportUnit(
                quiz.getName(),
                quiz.getDescription(),
                quiz.getCompletionMessage(),
                quiz.getGroups().stream().sorted().toList(),
                quiz.getRecurrence(),
                exportDate(quiz.getStartDate()),
                exportDate(quiz.getEndDate()),
                Boolean.TRUE.equals(quiz.getPublished()),
                questions
            ));
        }
        return new QuizExportResponse(QuizExportResponse.FORMAT, QuizExportResponse.VERSION, exportDate(exportedAt), units);
    }

    private String exportDate(LocalDateTime date) {
        return date == null ? null : date.format(EXPORT_DATE);
    }

    private String format(LocalDateTime date) {
        return date == null ? null : date.toString();
    }

}
