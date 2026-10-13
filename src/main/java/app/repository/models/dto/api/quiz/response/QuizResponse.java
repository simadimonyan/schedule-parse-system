package app.repository.models.dto.api.quiz.response;

import app.repository.models.dto.api.quiz.unit.QuestionUnit;

import java.util.List;

/** status: draft, scheduled, active, closed */
public record QuizResponse(
    Long id,
    String name,
    String description,
    String completionMessage,
    String status,
    List<String> groups,
    List<QuestionUnit> questions,
    String startDate,
    String endDate,
    Long applications
) {}
