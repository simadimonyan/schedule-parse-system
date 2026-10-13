package app.repository.models.dto.api.quiz.unit;

/**
 * Ответ на один вопрос: answerId — для вопросов с выбором
 * (для MULTIPLE по строке на каждый вариант), value — текст или оценка
 */
public record SubmitUnit(Long questionId, Long answerId, String value) {}
