package com.siose.ff.identity.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

class IdentityJwtAuthenticationConverterTest {

    private final IdentityJwtAuthenticationConverter converter = new IdentityJwtAuthenticationConverter();

    @Test
    void shouldRejectATenantIdThatBreaksTheFormat() {
        Jwt jwt = Jwt.withTokenValue("verified-by-decoder")
                .header("alg", "RS256")
                .subject("u-1")
                .claim("tenant_id", "FF.EU Freight")
                .build();

        assertThatThrownBy(() -> converter.convert(jwt))
                .isInstanceOf(InvalidBearerTokenException.class)
                .hasMessageContaining("invalid");
    }
}
