package app.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

@Slf4j
public class AccessFilter extends OncePerRequestFilter {

    /**
     * Право на правку расписания. Совпадает с тем, что проверяют {@code @PreAuthorize} на
     * контроллерах, и с ролью {@code schedule} из Keycloak после приведения имени.
     */
    private static final String SCHEDULE_ROLE = "ROLE_SCHEDULE";

    private final AuthenticationManager authenticationManager;
    private final AdminAccess adminAccess;


    public AccessFilter(AuthenticationManager authenticationManager, AdminAccess adminAccess) {
        this.authenticationManager = authenticationManager;
        this.adminAccess = adminAccess;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        // CORS preflight — пропускаем без проверки токена, его отработает CorsFilter
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        if (path.startsWith("/login") || path.startsWith("/swagger-ui/") || path.startsWith("/v3/api-docs") || path.startsWith("/error") || path.contains("/online/sse")) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        // Токен Keycloak уже разобран resource-server'ом выше: его проверяет подпись, и
        // сверять его с общим токеном чтения бессмысленно — он не совпадёт никогда, а человек
        // за шлюзом получал бы 403 вместо честной проверки.
        if (header != null && header.startsWith("Bearer ") && !looksLikeJwt(header.substring(7))) {
            String token = header.substring(7);

            UsernamePasswordAuthenticationToken authRequest = new UsernamePasswordAuthenticationToken(token, null);
            try {
                Authentication authResult = authenticationManager.authenticate(authRequest);
                SecurityContextHolder.getContext().setAuthentication(authResult);
            } catch (AuthenticationException ex) {
                SecurityContextHolder.clearContext();
                log.warn("Выполнен FORBIDDEN запрос: {}", path);
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid API token");
                return;
            }
        }

        grantScheduleRole(request);

        filterChain.doFilter(request, response);
    }

    /**
     * Административный токен даёт то же право, что и роль из Keycloak.
     *
     * <p>Право на правку расписания названо один раз — в {@code @PreAuthorize} на методе, — а
     * доказать его можно двумя способами: ролью {@code schedule} в токене человека или общим
     * административным токеном, которым ходят кабинет и служебные скрипты. Второй путь
     * переходный: он был единственным до того, как роли начали проверяться, и снять его можно,
     * когда роль будет выдана всем, кому положено.
     *
     * <p>Роль добавляется к уже собранным правам, а не заменяет их: у человека за шлюзом
     * остаются его собственные, у общего токена чтения — его {@code ROLE_USER}.
     */
    private void grantScheduleRole(HttpServletRequest request) {
        if (!adminAccess.granted(request.getHeader(AdminAccess.HEADER))) return;

        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (current != null && current.getAuthorities().stream()
                .anyMatch(authority -> SCHEDULE_ROLE.equals(authority.getAuthority()))) {
            return;
        }

        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        if (current != null) authorities.addAll(current.getAuthorities());
        authorities.add(new SimpleGrantedAuthority(SCHEDULE_ROLE));

        // JWT остаётся JWT: подменив тип, мы отобрали бы у контроллеров разбор токена, а
        // вместе с ним и всё, что из него читается
        Authentication granted = current instanceof JwtAuthenticationToken jwt
                ? new JwtAuthenticationToken(jwt.getToken(), authorities, jwt.getName())
                : new UsernamePasswordAuthenticationToken(
                        current == null ? "adminToken" : current.getPrincipal(),
                        current == null ? null : current.getCredentials(),
                        authorities);

        SecurityContextHolder.getContext().setAuthentication(granted);
    }

    /**
     * Похож ли токен на JWT.
     *
     * <p>Различаем по форме, а не по попытке разбора: общий токен чтения — обычная
     * строка без точек, JWT — три части через точку. Подпись здесь не проверяется,
     * это работа resource-server'а; задача метода — только выбрать, кому отдать токен.
     *
     * <p>Тем же признаком пользуется {@code BearerTokenResolver} в конфигурации
     * безопасности, поэтому правило живёт в одном месте: разойдись эти две проверки,
     * токен провалился бы между фильтрами и запрос получил бы 401 вместо ответа.
     */
    public static boolean looksLikeJwt(String token) {
        return token != null && token.chars().filter(ch -> ch == '.').count() == 2;
    }

}
