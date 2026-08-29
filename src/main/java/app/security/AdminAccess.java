package app.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Проверка административного токена.
 *
 * <p>Токен приходит заголовком {@code X-Admin-Token}, а не телом запроса, как у смены
 * чётности недели. Причина простая: тело у создания слота или изменения занято самим
 * объектом, и втиснуть туда же строку токена нечем.
 *
 * <p>Через {@code Authorization} его тоже не передать —
 * {@link TokenAuthenticationProvider} сверяет заголовок только с общим токеном доступа и
 * административный отклонит как чужой ещё в фильтре, до контроллера.
 *
 * <p>Сам по себе класс ничего не запрещает: он только отвечает, подошёл ли токен. Отказ
 * выносит {@code @PreAuthorize} на методе — {@code AccessFilter} по этому ответу выдаёт
 * запросу роль {@code ROLE_SCHEDULE}, ту же, что приезжает в токене человека из Keycloak.
 * Так право названо в одном месте, а доказать его можно двумя способами.
 */
@Slf4j
@Component
public class AdminAccess {

    public static final String HEADER = "X-Admin-Token";

    @Value("${schedule.admin.token}")
    private String adminToken;

    /** Разрешена ли административная операция с этим токеном. */
    public boolean granted(String token) {
        if (token == null || token.isBlank()) return false;

        String value = token.startsWith("Bearer ") ? token.substring(7) : token;
        return value.equals(adminToken);
    }

}
