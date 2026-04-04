package app.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExternalConfiguration {

    @Value("${external.server.url}")
    private String url;

    @Bean
    public String getUrl() {
        return url;
    }

}
