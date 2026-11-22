package com.siose.ff.identity;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Base64;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class IdentityApiTest {

    private static final String USER = """
            {"username":"new.user","email":"new.user@example.com","password":"secret-pass","roles":["WAREHOUSE_OPERATOR"]}
            """;

    /** A tenant administrator of the given tenant, as the verified token would present it. */
    private static RequestPostProcessor adminOf(String tenantId) {
        return jwt().jwt(token -> token.claim("tenant_id", tenantId))
                .authorities(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"));
    }

    /** A planner of the given tenant. The guest token takes its tenant from this token. */
    private static RequestPostProcessor plannerOf(String tenantId) {
        return jwt().jwt(token -> token.claim("tenant_id", tenantId).claim("preferred_username", "nexus.planner"))
                .authorities(new SimpleGrantedAuthority("ROLE_LOGISTICS_PLANNER"));
    }

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void keycloak(DynamicPropertyRegistry registry) {
        registry.add("identity.keycloak.base-url", FakeKeycloak::baseUrl);
    }

    @AfterAll
    static void stopKeycloak() {
        FakeKeycloak.stop();
    }

    @Test
    void shouldOpenRefreshAndCloseASession() throws Exception {
        mockMvc.perform(post("/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"nexus.planner","password":"fflogistics2026"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-1"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-1"))
                .andExpect(jsonPath("$.expiresIn").value(300))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));

        mockMvc.perform(post("/sessions/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"refresh-1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-2"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-2"));

        mockMvc.perform(post("/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"refresh-1"}
                                """))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldRejectAWrongPassword() throws Exception {
        mockMvc.perform(post("/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"nexus.planner","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldPublishTheCachedJwksDocument() throws Exception {
        mockMvc.perform(get("/jwks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kid").value("test"));

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void shouldIssueAServiceTokenOnlyWithTheBrokerKey() throws Exception {
        mockMvc.perform(post("/service-tokens"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/service-tokens").header("X-Broker-Key", "wrong"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/service-tokens").header("X-Broker-Key", "ff-identity-broker-dev"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("service-access"));
    }

    @Test
    void shouldCreateAUserOnlyForATenantAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(USER))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/users")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_REPORT_VIEWER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(USER))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/users")
                        .with(adminOf("ff-eu-freight"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"new.user","email":"new.user@example.com","password":"secret-pass","roles":["SUPERUSER"]}
                                """))
                .andExpect(status().isBadRequest());

        FakeKeycloak.lastUserBody = null;
        FakeKeycloak.lastRoleBody = null;
        mockMvc.perform(post("/api/v1/users")
                        .with(adminOf("ff-eu-freight"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(USER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("user-42"))
                .andExpect(jsonPath("$.username").value("new.user"))
                .andExpect(jsonPath("$.tenantId").value("ff-eu-freight"));

        org.junit.jupiter.api.Assertions.assertTrue(FakeKeycloak.lastUserBody.contains("ff-eu-freight"));
        org.junit.jupiter.api.Assertions.assertTrue(FakeKeycloak.lastRoleBody.contains("WAREHOUSE_OPERATOR"));
    }

    @Test
    void shouldNeverCreateAUserInAnotherTenant() throws Exception {
        FakeKeycloak.lastUserBody = null;

        // The admin of ff-eu-freight tries to plant an administrator in ff-tr-distribution:
        // the request schema has no tenant field, so the request is rejected as a whole.
        mockMvc.perform(post("/api/v1/users")
                        .with(adminOf("ff-eu-freight"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"intruder","email":"intruder@example.com","password":"secret-pass",
                                 "tenantId":"ff-tr-distribution","roles":["TENANT_ADMIN"]}
                                """))
                .andExpect(status().isBadRequest());
        org.junit.jupiter.api.Assertions.assertNull(FakeKeycloak.lastUserBody, "no user may reach Keycloak");

        // Without the field, the new user lands in the admin's own tenant
        mockMvc.perform(post("/api/v1/users")
                        .with(adminOf("ff-tr-distribution"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(USER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value("ff-tr-distribution"));
        org.junit.jupiter.api.Assertions.assertTrue(FakeKeycloak.lastUserBody.contains("ff-tr-distribution"));
    }

    @Test
    void shouldRejectAnAdminTokenWhoseTenantBreaksTheFormat() throws Exception {
        FakeKeycloak.lastUserBody = null;

        mockMvc.perform(post("/api/v1/users")
                        .with(adminOf("FF.EU Freight"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(USER))
                .andExpect(status().isUnauthorized());

        org.junit.jupiter.api.Assertions.assertNull(FakeKeycloak.lastUserBody, "no user may reach Keycloak");
    }

    @Test
    void shouldRejectAnAdminTokenWithoutTenant() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(USER))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldOpenASessionForAnIntegrationClient() throws Exception {
        mockMvc.perform(post("/sessions/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"" + FakeKeycloak.INTEGRATION_CLIENT + "\",\"clientSecret\":\""
                                + FakeKeycloak.INTEGRATION_SECRET + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("integration-access"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.expiresIn").value(300));

        mockMvc.perform(post("/sessions/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"" + FakeKeycloak.INTEGRATION_CLIENT + "\",\"clientSecret\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/sessions/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"","clientSecret":"x"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldNeverHandOutATokenOfThePlatformsOwnClients() throws Exception {
        // The right secret of the service client: Keycloak would issue a token, the door does not open.
        mockMvc.perform(post("/sessions/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"ff-logistics-service","clientSecret":"ff-logistics-service-secret"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldCreateAnIntegrationClientOnlyForATenantAdminOfItsOwnTenant() throws Exception {
        String erp = """
                {"name":"erp"}
                """;
        mockMvc.perform(post("/api/v1/integration-clients").contentType(MediaType.APPLICATION_JSON).content(erp))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/integration-clients")
                        .with(jwt().jwt(token -> token.claim("tenant_id", "ff-eu-freight"))
                                .authorities(new SimpleGrantedAuthority("ROLE_LOGISTICS_PLANNER")))
                        .contentType(MediaType.APPLICATION_JSON).content(erp))
                .andExpect(status().isForbidden());

        FakeKeycloak.lastClientBody = null;
        mockMvc.perform(post("/api/v1/integration-clients")
                        .with(adminOf("ff-eu-freight"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"erp","tenantId":"ff-tr-distribution","roles":["TENANT_ADMIN"]}
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/integration-clients")
                        .with(adminOf("ff-eu-freight"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Our ERP"}
                                """))
                .andExpect(status().isBadRequest());
        org.junit.jupiter.api.Assertions.assertNull(FakeKeycloak.lastClientBody, "no client may reach Keycloak");

        FakeKeycloak.lastRoleBody = null;
        mockMvc.perform(post("/api/v1/integration-clients")
                        .with(adminOf("ff-eu-freight"))
                        .contentType(MediaType.APPLICATION_JSON).content(erp))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientId").value("integration-ff-eu-freight.erp"))
                .andExpect(jsonPath("$.clientSecret").value("generated-secret"))
                .andExpect(jsonPath("$.tenantId").value("ff-eu-freight"))
                .andExpect(jsonPath("$.roles[0]").value("LOGISTICS_PLANNER"));

        String client = FakeKeycloak.lastClientBody;
        org.junit.jupiter.api.Assertions.assertTrue(client.contains("\"claim.value\":\"ff-eu-freight\""), client);
        org.junit.jupiter.api.Assertions.assertTrue(client.contains("\"included.client.audience\":\"ff-logistics-api\""), client);
        org.junit.jupiter.api.Assertions.assertTrue(client.contains("\"serviceAccountsEnabled\":true"), client);
        org.junit.jupiter.api.Assertions.assertTrue(FakeKeycloak.lastRoleBody.contains("LOGISTICS_PLANNER"));
        org.junit.jupiter.api.Assertions.assertTrue(FakeKeycloak.lastRoleRemovalBody.contains("default-roles-ff-logistics"));

        mockMvc.perform(post("/api/v1/integration-clients")
                        .with(adminOf("ff-eu-freight"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"taken"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldExchangeAPlannersTokenForAGuestTokenOfThatTenant() throws Exception {
        mockMvc.perform(post("/api/v1/guest-tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dashboardId":"monthly"}
                                """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/guest-tokens")
                        .with(jwt().jwt(token -> token.claim("tenant_id", "ff-eu-freight")
                                        .claim("preferred_username", "nexus.operator"))
                                .authorities(new SimpleGrantedAuthority("ROLE_WAREHOUSE_OPERATOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dashboardId":"monthly"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/guest-tokens")
                        .with(plannerOf("ff-eu-freight"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dashboardId":"monthly","tenantId":"ff-tr-distribution"}
                                """))
                .andExpect(status().isForbidden());

        String body = mockMvc.perform(post("/api/v1/guest-tokens")
                        .with(plannerOf("ff-eu-freight"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dashboardId":"monthly"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("ff-eu-freight"))
                .andExpect(jsonPath("$.dashboardId").value("monthly"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        JsonNode token = JsonMapper.builder().build().readTree(body).get("token");
        JsonNode claims = JsonMapper.builder().build().readTree(
                Base64.getUrlDecoder().decode(token.asString().split("\\.")[1]));
        org.junit.jupiter.api.Assertions.assertEquals("ff-eu-freight", claims.get("app").get("tenant_id").asString());
        org.junit.jupiter.api.Assertions.assertEquals("monthly", claims.get("resources").get(0).get("id").asString());
    }
}
