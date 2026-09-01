package app.service.max;

import app.configuration.ExternalConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;
import java.util.function.Function;

/**
 * Транспорт до стороннего сервера расписания (rasp.imsit.ru).
 *
 * Сервер закрыт проверкой безопасности: любой запрос без cookie challenge_passed
 * получает 302 на auth0.imsit.ru, а запрос без браузерного User-Agent — 403.
 * Клиент сам проходит проверку, держит cookie (живёт 8 часов) и повторяет запрос.
 */
@Slf4j
@Service
public class ExternalClient {

    private static final String CHALLENGE_COOKIE = "challenge_passed";

    private final WebClient webClient;
    private final WebClient authClient;
    private final String serverUrl;

    private volatile String challenge;

    @Autowired
    public ExternalClient(
            @Qualifier("externalWebClient") WebClient webClient,
            @Qualifier("externalAuthWebClient") WebClient authClient,
            @Value("${external.rasp.url:" + ExternalConfiguration.DEFAULT_URL + "}") String serverUrl
    ) {
        this.webClient = webClient;
        this.authClient = authClient;
        this.serverUrl = serverUrl;
    }

    /**
     * GET к серверу расписания. Возвращает тело ответа либо null, если страницу
     * получить не удалось (сеть, проверка безопасности, ошибка сервера).
     */
    public String get(Function<UriBuilder, URI> uri) {
        String body = exchange(uri);
        if (body != null) return body;

        if (!passChallenge()) return null;
        return exchange(uri);
    }

    private String exchange(Function<UriBuilder, URI> uri) {
        try {
            return webClient.get()
                    .uri(uri)
                    .header(HttpHeaders.COOKIE, challenge == null ? "" : challenge)
                    .exchangeToMono(response -> {
                        HttpStatusCode status = response.statusCode();
                        if (status.is2xxSuccessful()) return response.bodyToMono(String.class);

                        log.warn("Сторонний сервер ответил {} — потребуется проверка безопасности", status.value());
                        return response.releaseBody().then(Mono.empty());
                    })
                    .block();
        }
        catch (Exception e) {
            log.error("Ошибка сети при обращении к стороннему серверу: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Проходит проверку безопасности: auth0 отдаёт cookie challenge_passed на весь домен
     * .imsit.ru, но только если в запросе есть параметр redirect.
     */
    private boolean passChallenge() {
        try {
            List<ResponseCookie> cookies = authClient.get()
                    .uri(builder -> builder.queryParam("redirect", serverUrl).build())
                    .exchangeToMono(response -> response.releaseBody()
                            .thenReturn(response.cookies().getOrDefault(CHALLENGE_COOKIE, List.of())))
                    .block();

            if (cookies == null) cookies = List.of();

            // auth0 присылает две cookie с этим именем: одну сбрасывающую (Max-Age=0) и рабочую
            String value = cookies.stream()
                    .filter(cookie -> !cookie.getMaxAge().isZero() && !cookie.getMaxAge().isNegative())
                    .map(ResponseCookie::getValue)
                    .findFirst()
                    .orElse(null);

            if (value == null) {
                log.error("Проверка безопасности не пройдена: сервер не выдал cookie {}", CHALLENGE_COOKIE);
                return false;
            }

            challenge = CHALLENGE_COOKIE + "=" + value;
            log.info("Проверка безопасности пройдена, cookie {} получена", CHALLENGE_COOKIE);
            return true;
        }
        catch (Exception e) {
            log.error("Ошибка при прохождении проверки безопасности: {}", e.getMessage());
            return false;
        }
    }

}
