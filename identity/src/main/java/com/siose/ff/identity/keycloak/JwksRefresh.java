package com.siose.ff.identity.keycloak;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Loads Keycloak's public keys at startup and refreshes them. A failed refresh keeps the previous
 * document so already-issued tokens can still be checked.
 */
@Component
public class JwksRefresh implements ApplicationRunner, HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(JwksRefresh.class);

    private final KeycloakBroker keycloak;
    private final JwksCache cache;
    private final JsonMapper jsonMapper;

    public JwksRefresh(KeycloakBroker keycloak, JwksCache cache, JsonMapper jsonMapper) {
        this.keycloak = keycloak;
        this.cache = cache;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        refresh();
    }

    @Scheduled(fixedDelay = 15_000)
    public void refresh() {
        try {
            String body = keycloak.fetchJwks();
            JsonNode node = jsonMapper.readTree(body);
            if (!node.isObject() || !node.path("keys").isArray() || node.path("keys").isEmpty()) {
                log.warn("JWKS refresh returned a document without keys");
                return;
            }
            cache.replace(body);
        } catch (RuntimeException ex) {
            log.warn("JWKS refresh failed: {}", ex.getMessage());
        }
    }

    @Override
    public Health health() {
        if (cache.isReady()) {
            return Health.up().build();
        }
        return Health.down().withDetail("reason", "Keycloak JWKS has not been loaded").build();
    }
}
