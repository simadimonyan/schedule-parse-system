package app.repository.models.dto.api.quiz.request;

import app.repository.models.dto.api.quiz.unit.QuizExportUnit;

import java.util.List;

/**
 * Импорт опросов: документ экспорта как есть плюс token — служебный ключ "Bearer <admin>"
 * (или токен человека в X-User-Token). format — "isuer.quizzes", version — 1
 */
public record QuizImportRequest(String token, String format, Integer version, String exportedAt, List<QuizExportUnit> quizzes) {}
