package com.siose.ff.identity.api;

import com.siose.ff.base.tenant.TenantIds;
import com.siose.ff.identity.api.dto.IdentityDtos.CreateIntegrationClientRequest;
import com.siose.ff.identity.api.dto.IdentityDtos.IntegrationClientResponse;
import com.siose.ff.identity.keycloak.KeycloakBroker;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * Integration clients for tenant administrators: the credentials a customer's system uses to call
 * the platform (client credentials, no user, no password).
 *
 * <p>As with users, the client always belongs to the administrator's own tenant, taken from the
 * verified access token, and gets only {@code LOGISTICS_PLANNER}: the request names neither tenant
 * nor role.
 */
@RestController
@RequestMapping("/api/v1/integration-clients")
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class IntegrationClientController {

    private final KeycloakBroker keycloak;

    public IntegrationClientController(KeycloakBroker keycloak) {
        this.keycloak = keycloak;
    }

    @PostMapping
    public ResponseEntity<IntegrationClientResponse> create(@AuthenticationPrincipal Jwt caller,
                                                            @Valid @RequestBody CreateIntegrationClientRequest request) {
        String tenantId = caller.getClaimAsString(UserController.TENANT_CLAIM);
        if (tenantId == null || tenantId.isBlank()) {
            throw new ResponseStatusException(FORBIDDEN, "Caller has no tenant");
        }
        if (!TenantIds.isValid(tenantId)) {
            throw new ResponseStatusException(UNAUTHORIZED, "Access token has an invalid tenant_id claim");
        }
        KeycloakBroker.IntegrationClient client = keycloak.createIntegrationClient(request.name(), tenantId);
        IntegrationClientResponse body = new IntegrationClientResponse(client.clientId(), client.clientSecret(), tenantId,
                List.of(KeycloakBroker.INTEGRATION_ROLE));
        return ResponseEntity.created(URI.create("/api/v1/integration-clients/" + client.clientId())).body(body);
    }
}
