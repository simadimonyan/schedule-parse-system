package app.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Клиент мастер-сервиса. Адрес задаётся переменной {@code MASTER_SERVICE_URL}: в compose
 * оба сервиса живут в сети {@code schedule_net}, поэтому по умолчанию это имя контейнера.
 */
@Configuration
public class MasterServiceConfiguration {

    @Value("${master.service.url}")
    private String masterUrl;

    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        return builder
                .baseUrl(masterUrl)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

}
