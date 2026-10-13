package app.repository.models.dto.api.quiz.unit;

import java.util.List;

/**
 * Опрос в переносимом документе экспорта/импорта: без id и без анкет.
 * Поля — как в QuizRequest; published = null при импорте — опубликован
 */
public record QuizExportUnit(
    String name,
    String description,
    String completionMessage,
    List<String> groups,
    String recurrence,
    String startDate,
    String endDate,
    Boolean published,
    List<QuestionExportUnit> questions
) {}
