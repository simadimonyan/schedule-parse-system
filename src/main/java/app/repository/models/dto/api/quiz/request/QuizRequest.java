package app.repository.models.dto.api.quiz.request;

import app.repository.models.dto.api.auth.AdminToken;
import app.repository.models.dto.api.quiz.unit.QuestionUnit;

import java.util.List;

/**
 * Создание и правка опроса. Даты — "yyyy-MM-dd" или "yyyy-MM-ddTHH:mm[:ss]" по Москве.
 * groups пустой или null — опрос для всех. published = null при создании — сразу опубликован.
 * При правке null-поля не меняются, questions можно заменить только пока нет ответов.
 * recurrence: null — один раз, "monthly" — раз в месяц (без учёта регистра);
 * при правке "" или "once" делает опрос снова одноразовым
 */
public record QuizRequest(
    AdminToken token,
    String name,
    String description,
    String completionMessage,
    List<String> groups,
    List<QuestionUnit> questions,
    String startDate,
    String endDate,
    Boolean published,
    String recurrence
) {}
