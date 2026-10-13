package app.repository.models.dto.api.quiz.request;

import app.repository.models.dto.api.quiz.unit.SubmitUnit;

import java.util.List;

/**
 * Анкета респондента. respondent — устойчивый идентификатор клиента
 * (uuid приложения, "tg:<id>", "max:<id>"), по нему не дают ответить дважды
 */
public record SubmitRequest(Long quizId, String respondent, String group, List<SubmitUnit> answers) {}
