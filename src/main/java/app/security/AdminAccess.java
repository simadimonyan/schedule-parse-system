package app.security;

import app.repository.models.dto.api.auth.AdminToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

/**
 * Кто может звать административные ручки.
 *
 * Два пути: служебный ключ `schedule.admin.token` в теле запроса — им ходят боты
 * и скрипты; либо токен человека из кабинета ИСУЭР в заголовке {@link #HEADER}
 * с одной из нужных ролей. Кабинет статических секретов не держит, поэтому
 * для него второй путь — единственный
 */
@Slf4j
@Component
public class AdminAccess {

    /** Заголовок Authorization занят ключом доступа клиента, токен человека идёт отдельно */
    public static final String HEADER = "X-User-Token";

    public static final String DISPATCHER = "dispatcher";
    public static final String HEAD = "head";

    @Value("${schedule.admin.token}")
    private String adminToken;

    private final UserTokenVerifier verifier;

    public AdminAccess(UserTokenVerifier verifier) {
        this.verifier = verifier;
    }

    public boolean allowed(AdminToken body, String userToken, String... roles) {
        if (body != null && body.token() != null && body.token().startsWith("Bearer ") && body.token().substring(7).equals(adminToken)) {
            return true;
        }
        if (userToken == null || userToken.isBlank()) return false;
        String jwt = userToken.startsWith("Bearer ") ? userToken.substring(7) : userToken;
        Set<String> granted = verifier.verify(jwt.trim()).orElse(Set.of());
        return Arrays.stream(roles).anyMatch(granted::contains);
    }

}
