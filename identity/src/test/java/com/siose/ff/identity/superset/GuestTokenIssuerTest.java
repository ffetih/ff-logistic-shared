package com.siose.ff.identity.superset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class GuestTokenIssuerTest {

    private static final String SECRET = "test-superset-guest-token-secret-value";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void signsTheClaimsSupersetReads() throws Exception {
        GuestTokenIssuer issuer = new GuestTokenIssuer(SECRET, "ff-logistic", Duration.ofMinutes(5));
        Instant issuedAt = Instant.parse("2026-11-22T08:00:00Z");

        GuestTokenIssuer.Signed signed = issuer.sign("ff-eu-freight", "nexus.planner", "monthly", issuedAt);

        String[] parts = signed.token().split("\\.");
        assertEquals(3, parts.length);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expected = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal((parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8)));
        assertEquals(expected, parts[2]);

        JsonNode claims = JSON.readTree(Base64.getUrlDecoder().decode(parts[1]));
        assertEquals("guest", claims.get("type").asString());
        assertEquals("ff-logistic", claims.get("aud").asString());
        assertEquals(issuedAt.plus(Duration.ofMinutes(5)).getEpochSecond(), claims.get("exp").asLong());
        assertEquals("ff-eu-freight", claims.get("app").get("tenant_id").asString());
        assertEquals("nexus.planner", claims.get("user").get("username").asString());
        assertEquals(1, claims.get("resources").size());
        assertEquals("dashboard", claims.get("resources").get(0).get("type").asString());
        assertEquals("monthly", claims.get("resources").get(0).get("id").asString());
        assertEquals("tenant_id = 'ff-eu-freight'", claims.get("rls_rules").get(0).get("clause").asString());
    }

    @Test
    void refusesToSignWhenTheSecretIsMissing() {
        GuestTokenIssuer issuer = new GuestTokenIssuer("  ", "ff-logistic", Duration.ofMinutes(5));

        assertThrows(ResponseStatusException.class,
                () -> issuer.sign("ff-eu-freight", "nexus.planner", "monthly", Instant.now()));
    }
}
