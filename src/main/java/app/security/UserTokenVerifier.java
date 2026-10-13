package app.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Проверка токена человека, вошедшего в кабинет ИСУЭР через Keycloak.
 *
 * Подпись сверяется с открытыми ключами реалма (JWKS), срок — по `exp`.
 * Библиотеки для JWT в сервисе нет, а build.gradle.kts на прод не уезжает,
 * поэтому RS256 проверяется средствами JDK. Ключи кешируются и перечитываются,
 * когда приходит токен с незнакомым `kid` (Keycloak сменил ключ), но не чаще раза в минуту
 */
@Slf4j
@Component
public class UserTokenVerifier {

    private static final long JWKS_REFRESH_MS = 60_000L;
    private static final long CLOCK_SKEW_SECONDS = 30L;

    /** Дефолт в коде: деплой не обновляет application.properties на сервере */
    @Value("${admin.auth.jwks-url:https://myimsit.ru/realms/schedule/protocol/openid-connect/certs}")
    private String jwksUrl;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Map<String, PublicKey> keys = new ConcurrentHashMap<>();
    private volatile long keysLoadedAt = 0L;

    /**
     * Роли из проверенного токена — последние части имён (`isms-dispatcher` → `dispatcher`),
     * как их понимает кабинет. Токен не прошёл проверку — пустой Optional
     */
    public Optional<Set<String>> verify(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return Optional.empty();

            JsonNode header = mapper.readTree(decode(parts[0]));
            if (!"RS256".equals(header.path("alg").asText())) return Optional.empty();
            PublicKey key = key(header.path("kid").asText());
            if (key == null) return Optional.empty();

            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(key);
            signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!signature.verify(Base64.getUrlDecoder().decode(parts[2]))) return Optional.empty();

            JsonNode claims = mapper.readTree(decode(parts[1]));
            long now = System.currentTimeMillis() / 1000;
            if (!claims.has("exp") || claims.path("exp").asLong() + CLOCK_SKEW_SECONDS < now) return Optional.empty();

            return Optional.of(roles(claims));
        } catch (Exception e) {
            log.warn("Токен кабинета не прошёл проверку: {}", e.getMessage());
            return Optional.empty();
        }
    }

    static Set<String> roles(JsonNode claims) {
        Set<String> roles = new HashSet<>();
        List<JsonNode> lists = new ArrayList<>();
        lists.add(claims.path("realm_access").path("roles"));
        claims.path("resource_access").forEach(client -> lists.add(client.path("roles")));
        for (JsonNode list : lists) {
            for (JsonNode role : list) {
                String[] tail = role.asText().toLowerCase(Locale.ROOT).split("[.\\-:/]");
                roles.add(tail[tail.length - 1]);
            }
        }
        return roles;
    }

    private PublicKey key(String kid) throws Exception {
        PublicKey key = keys.get(kid);
        if (key != null) return key;
        if (System.currentTimeMillis() - keysLoadedAt < JWKS_REFRESH_MS) return null;
        loadKeys();
        return keys.get(kid);
    }

    private synchronized void loadKeys() throws Exception {
        if (System.currentTimeMillis() - keysLoadedAt < JWKS_REFRESH_MS) return;
        keysLoadedAt = System.currentTimeMillis();
        HttpResponse<String> response = http.send(
            HttpRequest.newBuilder(URI.create(jwksUrl)).timeout(Duration.ofSeconds(5)).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );
        if (response.statusCode() != 200) {
            log.warn("Ключи Keycloak не получены: {} ответил {}", jwksUrl, response.statusCode());
            return;
        }
        KeyFactory factory = KeyFactory.getInstance("RSA");
        for (JsonNode jwk : mapper.readTree(response.body()).path("keys")) {
            if (!"RSA".equals(jwk.path("kty").asText()) || "enc".equals(jwk.path("use").asText())) continue;
            BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(jwk.path("n").asText()));
            BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(jwk.path("e").asText()));
            keys.put(jwk.path("kid").asText(), factory.generatePublic(new RSAPublicKeySpec(modulus, exponent)));
        }
        log.info("Ключи Keycloak загружены: {}", keys.keySet());
    }

    private static String decode(String part) {
        return new String(Base64.getUrlDecoder().decode(part), StandardCharsets.UTF_8);
    }

}
