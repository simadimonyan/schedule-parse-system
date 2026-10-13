package app.repository.models.dto.api.quiz.response;

import app.repository.models.dto.api.quiz.unit.QuizExportUnit;

import java.util.List;

/**
 * Переносимое между стендами описание опросов: format "isuer.quizzes", version 1,
 * exportedAt — время выгрузки по Москве "yyyy-MM-ddTHH:mm:ss"
 */
public record QuizExportResponse(String format, Integer version, String exportedAt, List<QuizExportUnit> quizzes) {

    public static final String FORMAT = "isuer.quizzes";
    public static final int VERSION = 1;

}
