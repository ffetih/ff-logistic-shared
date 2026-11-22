package com.siose.ff.identity.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class IdentityDtos {

    private IdentityDtos() {
    }

    public record SessionRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record LogoutRequest(@NotBlank String refreshToken) {
    }

    public record TokenResponse(String accessToken, String refreshToken, long expiresIn, String tokenType) {
    }

    /** A system of a customer signing in with the client it was issued ({@code integration-*}). */
    public record ClientSessionRequest(@NotBlank String clientId, @NotBlank String clientSecret) {
    }

    /** {@code name} becomes the client id {@code integration-<name>}. */
    public record CreateIntegrationClientRequest(
            @NotBlank @jakarta.validation.constraints.Pattern(regexp = "[a-z0-9][a-z0-9-]{1,40}",
                    message = "2 to 41 lowercase letters, digits or -, starting with a letter or digit") String name
    ) {
    }

    /** The secret is shown once, in this answer; it cannot be read again through this API. */
    public record IntegrationClientResponse(String clientId, String clientSecret, String tenantId, List<String> roles) {
    }

    /** No tenant here: a new user always joins the calling administrator's tenant. */
    public record CreateUserRequest(
            @NotBlank String username,
            @NotBlank @Email String email,
            @NotBlank String password,
            @NotEmpty List<@NotBlank String> roles,
            // Optional; shown in Keycloak and in tokens (given_name, family_name).
            @Size(max = 255) String firstName,
            @Size(max = 255) String lastName
    ) {
    }

    public record UserResponse(String id, String username, String tenantId, List<String> roles) {
    }

    /**
     * One dashboard. {@code tenantId}, when present, has to be the caller's own tenant:
     * the signed token never takes a tenant from this body.
     */
    public record GuestTokenRequest(
            @NotBlank @jakarta.validation.constraints.Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{0,63}",
                    message = "a dashboard id is 1 to 64 letters, digits, dot, underscore or hyphen") String dashboardId,
            @jakarta.validation.constraints.Pattern(regexp = "[a-z0-9][a-z0-9-]{1,63}",
                    message = "a tenant id has 2 to 64 lower-case letters, digits or hyphens") String tenantId
    ) {
    }

    public record GuestTokenResponse(String token, String tenantId, String dashboardId, Instant expiresAt) {
    }
}
