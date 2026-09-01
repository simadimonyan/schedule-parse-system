package app.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class ExternalConfiguration {

    @Value("${external.server.url}")
    private String url;

    @Value("${external.auth.url}")
    private String authUrl;

    @Value("${external.user.agent}")
    private String userAgent;

    /** Страница расписания на весь учебный год весит несколько мегабайт — дефолтных 256 КБ не хватает. */
    @Value("${external.response.max.size}")
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
