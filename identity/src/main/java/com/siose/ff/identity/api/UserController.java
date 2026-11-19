package com.siose.ff.identity.api;

import com.siose.ff.base.tenant.TenantIds;
import com.siose.ff.identity.api.dto.IdentityDtos.CreateUserRequest;
import com.siose.ff.identity.api.dto.IdentityDtos.UserResponse;
import com.siose.ff.identity.keycloak.KeycloakBroker;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * User administration for tenant administrators.
 *
 * <p>The new user always belongs to the administrator's own tenant, taken from the verified
 * access token. The request cannot name a tenant, so an administrator cannot create users
 * (or administrators) in another tenant.
 */
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class UserController {

    static final String TENANT_CLAIM = "tenant_id";

    static final Set<String> ROLES = Set.of(
            "TENANT_ADMIN",
            "LOGISTICS_PLANNER",
            "WAREHOUSE_OPERATOR",
            "DISPATCHER",
            "REPORT_VIEWER");

    private final KeycloakBroker keycloak;

    public UserController(KeycloakBroker keycloak) {
        this.keycloak = keycloak;
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(
            @AuthenticationPrincipal Jwt caller,
            @Valid @RequestBody CreateUserRequest request
    ) {
        String tenantId = caller.getClaimAsString(TENANT_CLAIM);
        if (tenantId == null || tenantId.isBlank()) {
            throw new ResponseStatusException(FORBIDDEN, "Caller has no tenant");
        }
        if (!TenantIds.isValid(tenantId)) {
            throw new ResponseStatusException(UNAUTHORIZED, "Access token has an invalid tenant_id claim");
        }
        if (!ROLES.containsAll(request.roles())) {
            throw new ResponseStatusException(BAD_REQUEST, "Unknown role");
        }
        String id = keycloak.createUser(
                request.username(),
                request.email(),
                request.password(),
                tenantId,
                request.roles(),
                request.firstName(),
                request.lastName());
        UserResponse body = new UserResponse(id, request.username(), tenantId, request.roles());
        return ResponseEntity.created(URI.create("/api/v1/users/" + id)).body(body);
    }
}
