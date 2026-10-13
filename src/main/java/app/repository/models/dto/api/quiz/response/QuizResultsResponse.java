package app.repository.models.dto.api.quiz.response;

import java.util.List;

/** group и period — применённые фильтры (null — без фильтра) */
public record QuizResultsResponse(Long id, String name, String status, String group, String period, Long applications, List<QuestionResult> questions) {}
