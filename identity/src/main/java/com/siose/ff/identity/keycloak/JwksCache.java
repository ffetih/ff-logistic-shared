package com.siose.ff.identity.keycloak;

import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Last JWKS document fetched from Keycloak. Domain services read it from GET /jwks.
 */
@Component
public class JwksCache {

    private volatile String document;

    public Optional<String> document() {
        return Optional.ofNullable(document);
    }

    public void replace(String document) {
        this.document = document;
    }

    public boolean isReady() {
        return document != null;
    }
}
