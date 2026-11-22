package com.siose.ff.identity.api;

import com.siose.ff.base.tenant.TenantIds;
import com.siose.ff.identity.api.dto.IdentityDtos.GuestTokenRequest;
import com.siose.ff.identity.api.dto.IdentityDtos.GuestTokenResponse;
import com.siose.ff.identity.superset.GuestTokenIssuer;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Exchanges a planner's access token for a Superset guest token.
 * The guest token names that planner's tenant and the one dashboard in the body.
 * A body that names another tenant is refused. The signing secret stays here and in Superset.
 */
@RestController
@RequestMapping("/api/v1/guest-tokens")
@PreAuthorize("hasRole('LOGISTICS_PLANNER')")
public class GuestTokenController {

    private final GuestTokenIssuer issuer;

    public GuestTokenController(GuestTokenIssuer issuer) {
        this.issuer = issuer;
    }

    @PostMapping
    public GuestTokenResponse issue(@AuthenticationPrincipal Jwt caller, @Valid @RequestBody GuestTokenRequest request) {
        String tenantId = caller.getClaimAsString("tenant_id");
        if (tenantId == null || tenantId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Caller has no tenant");
        }
        if (!TenantIds.isValid(tenantId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Access token has an invalid tenant_id claim");
        }
        if (request.tenantId() != null && !request.tenantId().equals(tenantId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Guest token tenant must be the caller's tenant");
        }
        GuestTokenIssuer.Signed signed = issuer.sign(
                tenantId,
                caller.getClaimAsString("preferred_username"),
                request.dashboardId(),
                Instant.now());
        return new GuestTokenResponse(signed.token(), signed.tenantId(), signed.dashboardId(), signed.expiresAt());
    }
}
