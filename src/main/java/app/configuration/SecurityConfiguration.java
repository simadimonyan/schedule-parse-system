package app.configuration;

import app.security.AccessFilter;
import app.security.AdminAccess;
import app.security.TokenAuthenticationProvider;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;


@OpenAPIDefinition(
    info = @Info(title = "Сервис расписания", version = "v1", description = "API для работы с расписанием"),
    security = @SecurityRequirement(name = "Authorization")
)
@SecurityScheme(
    name = "Authorization",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT"
)
@Configuration
@EnableWebSecurity(debug = false)
public class SecurityConfiguration {

    private final TokenAuthenticationProvider tokenAuthenticationProvider;

    @Autowired
    public SecurityConfiguration(TokenAuthenticationProvider tokenAuthenticationProvider) {
        this.tokenAuthenticationProvider = tokenAuthenticationProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        return http.getSharedObject(AuthenticationManagerBuilder.class)
                .authenticationProvider(tokenAuthenticationProvider)
                .build();
    }

    @Bean
    public AccessFilter accessFilter(AuthenticationManager authenticationManager, AdminAccess adminAccess) {
        return new AccessFilter(authenticationManager, adminAccess);
    }

    /**
     * Resource-server забирает только токены Keycloak.
     *
     * <p>Его фильтр стоит в цепочке раньше {@link AccessFilter}, и по умолчанию он
     * берёт любой {@code Bearer}. Общий токен чтения публичного просмотрщика — не JWT,
     * разобрать его нельзя, и запрос заканчивался 401 ещё до проверки общим токеном.
     * Поэтому не-JWT здесь возвращается как «токена нет»: фильтр пропускает запрос
     * дальше, и его разбирает {@link AccessFilter}.
     */
    @Bean
    public BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        return request -> {
            String token;
            try {
                token = delegate.resolve(request);
            } catch (OAuth2AuthenticationException ex) {
                // Кривой заголовок — не наш случай, пусть решает AccessFilter
                return null;
            }
            return AccessFilter.looksLikeJwt(token) ? token : null;
        };
    }

    /**
     * Роли Keycloak — в authorities.
     *
     * <p>По умолчанию resource-server кладёт в authorities только области из claim
     * {@code scope} с префиксом {@code SCOPE_}, а роли пользователя лежат отдельно, в
     * {@code realm_access.roles}, и Spring туда не смотрит. Пока конвертера не было,
     * {@code hasRole('ROLE_SCHEDULE')} не совпадало ни с чем: роль в токене приезжала, но до
     * проверки не доходила.
     *
     * <p>Имя приводится к верхнему регистру и получает префикс {@code ROLE_}: в realm'е роль
     * называется {@code schedule}, а {@code hasRole} ищет authority {@code ROLE_SCHEDULE}.
     * Роли realm'а, а не клиента: они одни и те же для всех клиентов, которыми входит человек,
     * и не зависят от того, пришёл он из кабинета или из Postman.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new LinkedHashSet<>(scopes.convert(jwt));
            for (String role : realmRoles(jwt)) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase(Locale.ROOT)));
            }
            return authorities;
        });
        return converter;
    }

    private static List<String> realmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }

    /**
     * Origin'ы, которым разрешён браузерный доступ.
     *
     * <p>Список был зашит в код прод-доменами, и стенд в него не попадал: запрос с
     * localhost проходил проверку токена, а затем отбивался CorsFilter'ом с кодом 403 —
     * тем же, каким отвечает нехватка прав. Со стороны это выглядело как «роли не
     * выдались», хотя роли были на месте.
     *
     * <p>Звёздочки здесь быть не может: вместе с {@code allowCredentials} она запрещена
     * спецификацией, а по существу открыла бы API любому сайту, куда зайдёт вошедший
     * сотрудник.
     */
    @Value("${schedule.allowed-origins:https://myimsit.ru,https://app.myimsit.ru}")
    private List<String> allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // X-Admin-Token — заголовок административных операций (правка слотов, изменений,
        // графика занятости, переключение версий). Без него в списке браузер отсечёт запрос
        // ещё на preflight, до сервера
        config.setAllowedHeaders(List.of(
                "Authorization", "X-Admin-Token", "Content-Type", "X-Requested-With", "Accept", "Cache-Control"));
        config.setExposedHeaders(List.of("Content-Type", "Cache-Control"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AccessFilter accessFilter) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(context ->
                    context.requireExplicitSave(false)
                )
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/configuration/online/sse").permitAll()
                        .requestMatchers(
                                "/api/v1/teachers/**",
                                "/api/v1/groups/**",
                                "/api/v1/configuration/**",
                                "/api/v1/versions/**",
                                "/api/v1/slots/**",
                                "/api/v1/changes/**",
                                "/api/v1/work-schedules/**",
                                "/api/v1/schedule/**",
                                "/api/v1/quizzes/**",
                                "/minio-webhook"
                        )
                        .authenticated()
                        .anyRequest().permitAll()
                )
                // Второй способ доказать личность — токен Keycloak. Им приходит человек
                // из личного кабинета через шлюз; общий токен чтения остаётся у публичного
                // просмотрщика. Какой из двух применить, решает AccessFilter по форме токена.
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .addFilterAfter(accessFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

}
