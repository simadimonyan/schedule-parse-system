package app.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Клиент мастер-сервиса. Адрес задаётся переменной {@code MASTER_SERVICE_URL}: в compose
 * оба сервиса живут в сети {@code schedule_net}, поэтому по умолчанию это имя контейнера.
 *
 * <p>Мастер спрашивает, кто пришёл. Удостоверение сервис получает сам: по
 * {@code client_credentials} в Keycloak под своим клиентом, токен обновляется без участия
 * кода вызовов — {@link MasterServiceManager} про авторизацию ничего не знает.
 *
 * <p>Режим выбирается тем, что настроено, и переключается переменными окружения без пересборки:
 * <ol>
 *   <li>задан {@code KEYCLOAK_CLIENT_SECRET} — ходим с JWT (целевое состояние);</li>
 *   <li>задан только {@code MASTER_LEGACY_TOKEN} — старый общий секрет заголовком
 *       {@code X-Service-Token} (переходное состояние, мастер о нём предупреждает в логах);</li>
 *   <li>не задано ничего — как было, без удостоверения. Мастер такие вызовы отвергнет,
 *       поэтому на старте предупреждаем.</li>
 * </ol>
 */
@Slf4j
@Configuration
public class MasterServiceConfiguration {

    /** Регистрация клиента в {@code spring.security.oauth2.client.registration.<id>}. */
    private static final String REGISTRATION_ID = "master";

    private static final String LEGACY_HEADER = "X-Service-Token";

    @Value("${master.service.url}")
    private String masterUrl;

    @Value("${spring.security.oauth2.client.registration.master.client-secret:}")
    private String clientSecret;

    @Value("${master.auth.legacy-token:}")
    private String legacyToken;

    /**
     * Менеджер токенов для вызовов от имени сервиса, а не пользователя: за такими вызовами
     * человека нет, поэтому клиент авторизуется своими учётными данными, а выданный токен
     * хранится в общем сервисе и переиспользуется до истечения.
     */
    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository registrations,
            OAuth2AuthorizedClientService authorizedClients) {

        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, authorizedClients);
        manager.setAuthorizedClientProvider(
                OAuth2AuthorizedClientProviderBuilder.builder()
                        .clientCredentials()
                        .build());
        return manager;
    }

    @Bean
    public WebClient webClient(WebClient.Builder builder, OAuth2AuthorizedClientManager clientManager) {
        WebClient.Builder configured = builder
                .baseUrl(masterUrl)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        if (StringUtils.hasText(clientSecret)) {
            var oauth = new ServletOAuth2AuthorizedClientExchangeFilterFunction(clientManager);
            oauth.setDefaultClientRegistrationId(REGISTRATION_ID);
            log.info("Мастер-сервис: вызовы идут с токеном Keycloak (клиент {})", REGISTRATION_ID);
            return configured.filter(oauth).build();
        }

        if (StringUtils.hasText(legacyToken)) {
            log.warn("Мастер-сервис: вызовы идут по старому общему секрету. "
                    + "Это переходный режим — задай KEYCLOAK_CLIENT_SECRET");
            return configured.defaultHeader(LEGACY_HEADER, legacyToken).build();
        }

        log.error("Мастер-сервис: вызовы идут без удостоверения. Если у мастера включена "
                + "проверка (MASTER_AUTH_ENABLED=true), он ответит 401");
        return configured.build();
    }

}
