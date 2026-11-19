package com.siose.ff.identity.keycloak;

import com.siose.ff.identity.api.dto.IdentityDtos.TokenResponse;
import com.siose.ff.identity.config.IdentityProperties;
import io.micrometer.observation.ObservationRegistry;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * Keycloak protocol and admin calls. No other process in the platform is given these URLs or secrets.
 */
@Component
public class KeycloakBroker {

    private final IdentityProperties properties;
    private final RestClient keycloak;
    private final Object adminLock = new Object();
    private String adminToken;
    private Instant adminTokenExpiry = Instant.EPOCH;

    /** {@code observations}: every call to Keycloak is a span of the request that made it. */
    public KeycloakBroker(IdentityProperties properties, ObservationRegistry observations) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.keycloak = RestClient.builder()
                .baseUrl(properties.getKeycloak().getBaseUrl())
                .requestFactory(factory)
                .observationRegistry(observations)
                .build();
    }

    public TokenResponse passwordGrant(String username, String password) {
        MultiValueMap<String, String> form = tokenForm();
        form.add("grant_type", "password");
        form.add("username", username);
        form.add("password", password);
        return token(postForm("/realms/" + realm() + "/protocol/openid-connect/token", form, this::sessionRejected));
    }

    public TokenResponse refresh(String refreshToken) {
        MultiValueMap<String, String> form = tokenForm();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        try {
            return token(postForm("/realms/" + realm() + "/protocol/openid-connect/token", form, this::sessionRejected));
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode().value() == 401) {
                throw new ResponseStatusException(UNAUTHORIZED, "Invalid refresh token");
            }
            throw ex;
        }
    }

    public void logout(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.getKeycloak().getApiClientId());
        form.add("refresh_token", refreshToken);
        try {
            keycloak.post()
                    .uri("/realms/" + realm() + "/protocol/openid-connect/logout")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw new ResponseStatusException(BAD_GATEWAY, "Keycloak rejected the logout");
        } catch (ResourceAccessException ex) {
            throw unreachable();
        }
    }

    /** Client ids that may sign in through {@code /sessions/client}: the integration clients of tenants. */
    public static final String INTEGRATION_CLIENT_PREFIX = "integration-";

    /** The only role an integration client gets: it acts like a planner of its tenant, no more. */
    public static final String INTEGRATION_ROLE = "LOGISTICS_PLANNER";

    /**
     * Client credentials of an integration client. Its token carries the client's fixed tenant and
     * its service account's roles; there is no refresh token, the client signs in again instead.
     */
    public TokenResponse clientCredentialsGrant(String clientId, String clientSecret) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        return token(postForm("/realms/" + realm() + "/protocol/openid-connect/token", form, this::clientRejected));
    }

    /**
     * Creates a confidential client for one system of a customer: {@code integration-<tenant>.<name>}
     * (the tenant in the id, so two tenants may both call theirs {@code erp}; the dot cannot occur in
     * either part, so no two pairs give the same id), with a
     * service account that has only {@link #INTEGRATION_ROLE}, the audience of the platform API, and
     * a hard-coded {@code tenant_id} claim, so its tokens always belong to this tenant. Keycloak makes
     * the secret; it is returned once.
     */
    public IntegrationClient createIntegrationClient(String name, String tenantId) {
        String admin = adminToken();
        String clientId = INTEGRATION_CLIENT_PREFIX + tenantId + "." + name;
        Map<String, Object> client = new java.util.LinkedHashMap<>();
        client.put("clientId", clientId);
        client.put("name", "Integration " + name + " (" + tenantId + ")");
        client.put("enabled", true);
        client.put("publicClient", false);
        client.put("serviceAccountsEnabled", true);
        client.put("standardFlowEnabled", false);
        client.put("directAccessGrantsEnabled", false);
        client.put("attributes", Map.of("tenant_id", tenantId));
        client.put("protocolMappers", List.of(
                Map.of("name", "tenant-id", "protocol", "openid-connect", "protocolMapper", "oidc-hardcoded-claim-mapper",
                        "config", Map.of("claim.name", "tenant_id", "claim.value", tenantId, "jsonType.label", "String",
                                "access.token.claim", "true", "id.token.claim", "false", "userinfo.token.claim", "false")),
                Map.of("name", "api-audience", "protocol", "openid-connect", "protocolMapper", "oidc-audience-mapper",
                        "config", Map.of("included.client.audience", properties.getKeycloak().getApiClientId(),
                                "access.token.claim", "true", "id.token.claim", "false"))));
        String id;
        try {
            ResponseEntity<Void> created = keycloak.post()
                    .uri("/admin/realms/" + realm() + "/clients")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(client)
                    .retrieve()
                    .toBodilessEntity();
            id = userId(created.getHeaders().getLocation());
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 409) {
                throw new ResponseStatusException(CONFLICT, "Integration client " + clientId + " already exists");
            }
            throw adminRejected(ex);
        } catch (ResourceAccessException ex) {
            throw unreachable();
        }
        JsonNode serviceAccount = adminGet(admin, "/admin/realms/" + realm() + "/clients/" + id + "/service-account-user");
        String serviceAccountId = requiredText(serviceAccount, "id");
        assignRole(admin, serviceAccountId, INTEGRATION_ROLE);
        // Keycloak gives every new service account the realm's default roles (offline_access,
        // uma_authorization, the account console): an integration gets the planner role only.
        mapRole(admin, serviceAccountId, "default-roles-" + realm(), HttpMethod.DELETE);
        JsonNode secret = adminGet(admin, "/admin/realms/" + realm() + "/clients/" + id + "/client-secret");
        return new IntegrationClient(clientId, requiredText(secret, "value"));
    }

    /** A new integration client and its secret. */
    public record IntegrationClient(String clientId, String clientSecret) {
    }

    private JsonNode adminGet(String admin, String path) {
        try {
            return keycloak.get()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            throw adminRejected(ex);
        } catch (ResourceAccessException ex) {
            throw unreachable();
        }
    }

    public TokenResponse serviceToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.getKeycloak().getServiceClientId());
        form.add("client_secret", properties.getKeycloak().getServiceClientSecret());
        return token(postForm("/realms/" + realm() + "/protocol/openid-connect/token", form, this::brokerRejected));
    }

    public String fetchJwks() {
        try {
            byte[] body = keycloak.get()
                    .uri("/realms/" + realm() + "/protocol/openid-connect/certs")
                    .retrieve()
                    .body(byte[].class);
            if (body == null) {
                throw new ResponseStatusException(BAD_GATEWAY, "Keycloak JWKS response was empty");
            }
            return new String(body, java.nio.charset.StandardCharsets.UTF_8);
        } catch (RestClientResponseException ex) {
            throw new ResponseStatusException(BAD_GATEWAY, "Keycloak JWKS request failed");
        } catch (ResourceAccessException ex) {
            throw unreachable();
        }
    }

    /**
     * Creates an enabled user with a password and the tenant. Keycloak checks the user against the
     * realm's declared user profile ({@code deploy/platform/keycloak/user-profile.json}): an attribute the
     * profile does not declare is dropped without an error, so {@code tenant_id} must be declared there.
     */
    public String createUser(String username, String email, String password, String tenantId, List<String> roles,
                             String firstName, String lastName) {
        String admin = adminToken();
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("username", username);
        payload.put("email", email);
        payload.put("enabled", true);
        payload.put("emailVerified", true);
        if (firstName != null && !firstName.isBlank()) {
            payload.put("firstName", firstName);
        }
        if (lastName != null && !lastName.isBlank()) {
            payload.put("lastName", lastName);
        }
        payload.put("attributes", Map.of("tenant_id", List.of(tenantId)));
        payload.put("credentials", List.of(Map.of(
                "type", "password",
                "value", password,
                "temporary", false)));
        String userId;
        try {
            ResponseEntity<Void> created = keycloak.post()
                    .uri("/admin/realms/" + realm() + "/users")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            userId = userId(created.getHeaders().getLocation());
        } catch (RestClientResponseException ex) {
            throw adminRejected(ex);
        } catch (ResourceAccessException ex) {
            throw unreachable();
        }

        for (String role : roles) {
            assignRole(admin, userId, role);
        }
        return userId;
    }

    private void assignRole(String admin, String userId, String roleName) {
        mapRole(admin, userId, roleName, HttpMethod.POST);
    }

    /** Adds (POST) or removes (DELETE) one realm role of a user. */
    private void mapRole(String admin, String userId, String roleName, HttpMethod method) {
        JsonNode role;
        try {
            role = keycloak.get()
                    .uri("/admin/realms/" + realm() + "/roles/" + roleName)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            throw adminRejected(ex);
        } catch (ResourceAccessException ex) {
            throw unreachable();
        }
        Map<String, String> representation = Map.of("id", requiredText(role, "id"), "name", requiredText(role, "name"));
        try {
            keycloak.method(method)
                    .uri("/admin/realms/" + realm() + "/users/" + userId + "/role-mappings/realm")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(List.of(representation))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw adminRejected(ex);
        } catch (ResourceAccessException ex) {
            throw unreachable();
        }
    }

    private String adminToken() {
        synchronized (adminLock) {
            if (adminToken != null && Instant.now().isBefore(adminTokenExpiry)) {
                return adminToken;
            }
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "password");
            form.add("client_id", "admin-cli");
            form.add("username", properties.getKeycloak().getAdminUsername());
            form.add("password", properties.getKeycloak().getAdminPassword());
            JsonNode node = postForm("/realms/master/protocol/openid-connect/token", form, this::brokerRejected);
            adminToken = requiredText(node, "access_token");
            long expiresIn = node.path("expires_in").asLong(60);
            adminTokenExpiry = Instant.now().plusSeconds(Math.max(1, expiresIn - 10));
            return adminToken;
        }
    }

    private JsonNode postForm(String path, MultiValueMap<String, String> form, java.util.function.Function<RestClientResponseException, ResponseStatusException> onError) {
        try {
            return keycloak.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            throw onError.apply(ex);
        } catch (ResourceAccessException ex) {
            throw unreachable();
        }
    }

    private MultiValueMap<String, String> tokenForm() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.getKeycloak().getApiClientId());
        return form;
    }

    private static TokenResponse token(JsonNode node) {
        String refresh = node.hasNonNull("refresh_token") ? node.get("refresh_token").asString() : null;
        return new TokenResponse(
                requiredText(node, "access_token"),
                refresh,
                node.path("expires_in").asLong(),
                node.path("token_type").asString("Bearer"));
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull() || value.asString().isBlank()) {
            throw new ResponseStatusException(BAD_GATEWAY, "Keycloak response is missing " + field);
        }
        return value.asString();
    }

    private static String userId(URI location) {
        if (location == null || location.getPath() == null || location.getPath().isBlank()) {
            throw new ResponseStatusException(BAD_GATEWAY, "Keycloak did not return a user id");
        }
        String path = location.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private String realm() {
        return properties.getKeycloak().getRealm();
    }

    private ResponseStatusException sessionRejected(RestClientResponseException ex) {
        int code = ex.getStatusCode().value();
        if (code == 400 || code == 401) {
            return new ResponseStatusException(UNAUTHORIZED, "Invalid username or password");
        }
        return new ResponseStatusException(BAD_GATEWAY, "Keycloak rejected the call");
    }

    private ResponseStatusException clientRejected(RestClientResponseException ex) {
        int code = ex.getStatusCode().value();
        if (code == 400 || code == 401) {
            return new ResponseStatusException(UNAUTHORIZED, "Invalid client id or secret");
        }
        return new ResponseStatusException(BAD_GATEWAY, "Keycloak rejected the call");
    }

    private ResponseStatusException brokerRejected(RestClientResponseException ex) {
        return new ResponseStatusException(BAD_GATEWAY, "Keycloak rejected the call");
    }

    private ResponseStatusException adminRejected(RestClientResponseException ex) {
        int code = ex.getStatusCode().value();
        if (code == 409) {
            return new ResponseStatusException(CONFLICT, "User already exists");
        }
        if (code == 404) {
            return new ResponseStatusException(BAD_REQUEST, "Unknown role");
        }
        return new ResponseStatusException(BAD_GATEWAY, "Keycloak rejected the call");
    }

    private static ResponseStatusException unreachable() {
        return new ResponseStatusException(SERVICE_UNAVAILABLE, "Keycloak is unreachable");
    }
}
