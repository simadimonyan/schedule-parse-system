package app.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Включает {@code @PreAuthorize} на методах контроллеров.
 *
 * <p>Без этого класса аннотации в контроллерах — комментарий: Spring их не читает, и
 * {@code @PreAuthorize("hasRole('ROLE_SCHEDULE')")} над правкой слота не проверяет ничего.
 * Так оно и стояло: правки держал административный токен, который контроллеры сверяли руками,
 * а роль из Keycloak не участвовала.
 *
 * <p>Роль в токене появляется через конвертер в {@link SecurityConfiguration}: {@code schedule}
 * из {@code realm_access.roles} становится authority {@code ROLE_SCHEDULE}.
 */
@Configuration
@EnableMethodSecurity
public class MethodSecurityConfiguration {
}
