package app.repository.models.dto.api.quiz.unit;

import java.util.List;

/**
 * Вопрос опроса. type: SINGLE, MULTIPLE, TEXT, RATING.
 * answers — варианты для SINGLE/MULTIPLE, scale — верх шкалы для RATING (по умолчанию 5)
 */
public record QuestionUnit(Long id, String label, String description, String type, Boolean required, Integer scale, List<AnswerUnit> answers) {}
