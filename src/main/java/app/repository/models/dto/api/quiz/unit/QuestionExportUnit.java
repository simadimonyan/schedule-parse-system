package app.repository.models.dto.api.quiz.unit;

import java.util.List;

/** Вопрос в документе экспорта/импорта: как QuestionUnit, но без id у вопроса и вариантов */
public record QuestionExportUnit(String label, String description, String type, Boolean required, Integer scale, List<OptionExportUnit> answers) {}
