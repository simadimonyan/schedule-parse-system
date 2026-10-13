package app.repository.models.dto.api.quiz.response;

import java.util.List;

public record QuizResultsResponse(Long id, String name, String status, String group, Long applications, List<QuestionResult> questions) {}
