package app.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Настройки интеграции со сторонним сервером расписания (rasp.imsit.ru).
 *
 * У каждого свойства есть значение по умолчанию прямо в коде, и это не украшение:
 * деплой (.github/workflows) копирует на сервер только src/main/java, поэтому
 * application.properties на проде живёт своей жизнью и новых ключей не получает.
 * Ключи лежат в отдельном пространстве external.rasp.*, чтобы устаревшие значения
 * из серверного файла (в том числе старый адрес external.server.url) не перебивали
 * актуальные адреса из кода.
 */
@Configuration
public class ExternalConfiguration {

    public static final String DEFAULT_URL = "https://rasp.imsit.ru/";
    public static final String DEFAULT_AUTH_URL = "https://auth0.imsit.ru/";
    public static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
    public static final String DEFAULT_MAX_RESPONSE_SIZE = "33554432";

    @Value("${external.rasp.url:" + DEFAULT_URL + "}")
    private String url;

    @Value("${external.rasp.auth.url:" + DEFAULT_AUTH_URL + "}")
    private String authUrl;

    @Value("${external.rasp.user-agent:" + DEFAULT_USER_AGENT + "}")
    private String userAgent;

    /** Страница расписания на весь учебный год весит несколько мегабайт — дефолтных 256 КБ не хватает. */
    @Value("${external.rasp.max-response-size:" + DEFAULT_MAX_RESPONSE_SIZE + "}")
    private int maxResponseSize;

    @Bean("externalWebClient")
    public WebClient getExternalWebClient() {
        return build(url);
    }

    @Bean("externalAuthWebClient")
    public WebClient getExternalAuthWebClient() {
        return build(authUrl);
    }

    private WebClient build(String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "ru-RU,ru;q=0.9")
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(maxResponseSize))
                .build();
    }

}
