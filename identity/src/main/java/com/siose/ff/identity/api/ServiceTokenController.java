package com.siose.ff.identity.api;

import com.siose.ff.identity.api.dto.IdentityDtos.TokenResponse;
import com.siose.ff.identity.config.IdentityProperties;
import com.siose.ff.identity.keycloak.KeycloakBroker;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Exchanges the platform broker key for a Keycloak client-credentials token.
 * Callers never see the Keycloak client secret. The token has no tenant_id, so domain
 * services reject it; user calls keep using the session token.
 */
@RestController
public class ServiceTokenController {

    static final String BROKER_HEADER = "X-Broker-Key";

    private final IdentityProperties properties;
    private final KeycloakBroker keycloak;

    public ServiceTokenController(IdentityProperties properties, KeycloakBroker keycloak) {
        this.properties = properties;
        this.keycloak = keycloak;
    }

    @PostMapping("/service-tokens")
    public TokenResponse serviceToken(@RequestHeader(name = BROKER_HEADER, required = false) String brokerKey) {
        if (!matches(brokerKey, properties.getBrokerKey())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Broker key rejected");
        }
        return keycloak.serviceToken();
    }

    private static boolean matches(String provided, String expected) {
        if (provided == null || expected == null || expected.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                sha256(provided.getBytes(StandardCharsets.UTF_8)),
                sha256(expected.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
