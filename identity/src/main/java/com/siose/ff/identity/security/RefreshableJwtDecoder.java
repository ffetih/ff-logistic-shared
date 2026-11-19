package com.siose.ff.identity.security;

import com.siose.ff.identity.config.IdentityProperties;
import com.siose.ff.identity.keycloak.JwksCache;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.jwk.JWKSet;
import java.text.ParseException;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * Verifies tokens with the JWKS document this service already cached from Keycloak.
 */
@Component
public class RefreshableJwtDecoder implements JwtDecoder {

    private final JwksCache cache;
    private final IdentityProperties properties;
    private volatile String loadedDocument;
    private volatile JwtDecoder delegate;

    public RefreshableJwtDecoder(JwksCache cache, IdentityProperties properties) {
        this.cache = cache;
        this.properties = properties;
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        String document = cache.document().orElseThrow(() -> new JwtException("JWKS is not available"));
        JwtDecoder current = delegate;
        if (current == null || !document.equals(loadedDocument)) {
            synchronized (this) {
                if (delegate == null || !document.equals(loadedDocument)) {
                    delegate = build(document);
                    loadedDocument = document;
                }
                current = delegate;
            }
        }
        return current.decode(token);
    }

    private JwtDecoder build(String document) {
        try {
            JWKSet jwkSet = JWKSet.parse(document);
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSource(new ImmutableJWKSet<>(jwkSet)).build();
            String audience = properties.getKeycloak().getAudience();
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer(properties.getKeycloak().getIssuer()),
                    jwt -> validateAudience(jwt, audience)));
            return decoder;
        } catch (ParseException ex) {
            throw new JwtException("Cached JWKS document cannot be parsed", ex);
        }
    }

    private static OAuth2TokenValidatorResult validateAudience(Jwt jwt, String audience) {
        if (jwt.getAudience() != null && jwt.getAudience().contains(audience)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "required audience is missing", null));
    }
}
