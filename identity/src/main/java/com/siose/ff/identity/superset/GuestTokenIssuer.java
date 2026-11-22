package com.siose.ff.identity.superset;

import com.siose.ff.base.tenant.TenantIds;
import com.siose.ff.identity.config.IdentityProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Signs the guest token Superset accepts: HS256, {@code type=guest}, and {@code app.tenant_id}.
 * Superset copies that claim onto the analytics connection before the chart SQL.
 * The caller names one dashboard. The tenant is the caller's, already checked.
 */
@Component
public class GuestTokenIssuer {

    private static final Base64.Encoder URL = Base64.getUrlEncoder().withoutPadding();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final String secret;
    private final String audience;
    private final Duration ttl;

    @Autowired
    public GuestTokenIssuer(IdentityProperties properties) {
        IdentityProperties.Superset superset = properties.getSuperset();
        this.secret = superset.getGuestTokenSecret() == null ? "" : superset.getGuestTokenSecret().trim();
        this.audience = superset.getGuestTokenAudience();
        this.ttl = superset.getGuestTokenTtl();
    }

    GuestTokenIssuer(String secret, String audience, Duration ttl) {
        this.secret = secret == null ? "" : secret.trim();
        this.audience = audience;
        this.ttl = ttl;
    }

    public Signed sign(String tenantId, String username, String dashboardId, Instant issuedAt) {
        if (secret.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Guest token secret is not set");
        }
        tenantId = TenantIds.require(tenantId);
        String safeUsername = sanitize(username);
        if (safeUsername.isEmpty()) {
            safeUsername = "guest";
        }
        long issued = issuedAt.getEpochSecond();
        long expires = issuedAt.plus(ttl).getEpochSecond();

        ObjectNode payload = JSON.createObjectNode();
        ObjectNode user = payload.putObject("user");
        user.put("username", safeUsername);
        user.put("first_name", safeUsername);
        user.put("last_name", "guest");
        ArrayNode resources = payload.putArray("resources");
        ObjectNode resource = resources.addObject();
        resource.put("type", "dashboard");
        resource.put("id", dashboardId);
        ArrayNode rules = payload.putArray("rls_rules");
        rules.addObject().put("clause", "tenant_id = '" + tenantId + "'");
        payload.putObject("app").put("tenant_id", tenantId);
        payload.put("iat", issued);
        payload.put("exp", expires);
        payload.put("aud", audience);
        payload.put("type", "guest");

        String header = URL.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String body = URL.encodeToString(JSON.writeValueAsBytes(payload));
        String signingInput = header + "." + body;
        String token = signingInput + "." + URL.encodeToString(hmac(signingInput));
        return new Signed(token, tenantId, dashboardId, Instant.ofEpochSecond(expires));
    }

    private byte[] hmac(String signingInput) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not sign the Superset guest token", ex);
        }
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.replaceAll("[^a-zA-Z0-9_.@-]", "");
    }

    public record Signed(String token, String tenantId, String dashboardId, Instant expiresAt) {
    }
}
