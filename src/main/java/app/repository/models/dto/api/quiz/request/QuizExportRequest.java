package app.repository.models.dto.api.quiz.request;

import java.util.List;

/**
 * Экспорт опросов. token — служебный ключ "Bearer <admin>" (или токен человека в X-User-Token);
 * ids пусто или null — все опросы
 */
public record QuizExportRequest(String token, List<Long> ids) {}
