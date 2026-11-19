package com.siose.ff.identity.security;

import com.siose.ff.base.tenant.TenantIds;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Maps a verified access token to realm roles. Kept inside this service so the platform
 * library, which every domain service loads, does not gain a Keycloak client.
 *
 * <p>The tenant claim has to match {@link TenantIds}, the same rule the products enforce.
 * A token that breaks it is refused here, before any identity endpoint runs.
 */
public class IdentityJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String tenantId = jwt.getClaimAsString("tenant_id");
        if (tenantId == null || tenantId.isBlank()) {
            throw new InvalidBearerTokenException("Access token is missing the required 'tenant_id' claim");
        }
        if (!TenantIds.isValid(tenantId)) {
            throw new InvalidBearerTokenException("Access token has an invalid 'tenant_id' claim");
        }

        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        addRoles(authorities, jwt.getClaimAsMap("realm_access"));
        Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
        if (resourceAccess != null) {
            for (Object clientAccess : resourceAccess.values()) {
                if (clientAccess instanceof Map<?, ?> clientMap) {
                    addRoles(authorities, clientMap);
                }
            }
        }

        String username = jwt.getClaimAsString("preferred_username");
        return new JwtAuthenticationToken(jwt, authorities, username != null ? username : jwt.getSubject());
    }

    private static void addRoles(Set<GrantedAuthority> authorities, Map<?, ?> access) {
        if (access != null && access.get("roles") instanceof Collection<?> roles) {
            roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
        }
    }
}
