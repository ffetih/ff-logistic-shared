package com.siose.ff.identity.api;

import com.siose.ff.identity.api.dto.IdentityDtos.ClientSessionRequest;
import com.siose.ff.identity.api.dto.IdentityDtos.LogoutRequest;
import com.siose.ff.identity.api.dto.IdentityDtos.RefreshRequest;
import com.siose.ff.identity.api.dto.IdentityDtos.SessionRequest;
import com.siose.ff.identity.api.dto.IdentityDtos.TokenResponse;
import com.siose.ff.identity.keycloak.KeycloakBroker;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class SessionController {

    private final KeycloakBroker keycloak;

    public SessionController(KeycloakBroker keycloak) {
        this.keycloak = keycloak;
    }

    @PostMapping("/sessions")
    public TokenResponse open(@Valid @RequestBody SessionRequest request) {
        return keycloak.passwordGrant(request.username(), request.password());
    }

    /**
     * A customer's system signing in with its integration client. Only {@code integration-*} clients
     * may: the platform's own clients never get a token through this door. A wrong client is told no
     * more than a wrong secret.
     */
    @PostMapping("/sessions/client")
    public TokenResponse openForClient(@Valid @RequestBody ClientSessionRequest request) {
        if (!request.clientId().startsWith(KeycloakBroker.INTEGRATION_CLIENT_PREFIX)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid client id or secret");
        }
        return keycloak.clientCredentialsGrant(request.clientId(), request.clientSecret());
    }

    @PostMapping("/sessions/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return keycloak.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LogoutRequest request) {
        keycloak.logout(request.refreshToken());
    }
}
