package com.siose.ff.logistics;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.Arrays;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Callers with an already verified token, for MockMvc tests. */
public final class TestCallers {

    private TestCallers() {
    }

    /**
     * A tenant no other test uses. The tests share one in-memory database, so a test that counts
     * rows counts them in a tenant of its own.
     */
    public static String newTenant() {
        return "t-" + UUID.randomUUID().toString().substring(0, 13);
    }

    /** A user of this tenant holding these roles (without the ROLE_ prefix). */
    public static RequestPostProcessor as(String tenantId, String... roles) {
        GrantedAuthority[] authorities = Arrays.stream(roles)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .toArray(GrantedAuthority[]::new);
        return jwt()
                .jwt(token -> token.claim("tenant_id", tenantId).claim("preferred_username", "test.user"))
                .authorities(authorities);
    }
}
