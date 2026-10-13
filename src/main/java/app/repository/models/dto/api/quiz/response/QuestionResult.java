package app.repository.models.dto.api.quiz.response;

import java.util.List;

/**
 * Итог по вопросу: answered — сколько анкет ответили на вопрос,
 * options — распределение (для RATING по баллам), average — средний балл,
 * texts — свободные ответы, свежие первыми
 */
public record QuestionResult(Long id, String label, String type, Long answered, List<OptionResult> options, Double average, List<String> texts) {}
