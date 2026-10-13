package app.repository.models.dto.api.quiz.response;

/** Вариант ответа (или балл шкалы) и сколько раз его выбрали; share — доля ответивших на вопрос, 0..1 */
public record OptionResult(Long id, String label, Long count, Double share) {}
