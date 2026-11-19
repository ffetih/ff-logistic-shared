package com.siose.ff.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.siose.ff.identity.api.dto.IdentityDtos.TokenResponse;
import com.siose.ff.identity.config.IdentityProperties;
import com.siose.ff.identity.keycloak.KeycloakBroker;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The realm file and the broker against a real Keycloak 26, the version the cluster runs.
 * Skipped where no Docker is reachable. The in-process FakeKeycloak accepts any
 * user; Keycloak checks users against the realm's declared user profile, which once dropped
 * {@code tenant_id} silently and demanded names before a password sign-in.
 */
@EnabledIf("dockerAvailable")
class KeycloakRealmTest {

    /** The same image as deploy/platform/keycloak/deployment.yaml. */
    private static final String IMAGE = "quay.io/keycloak/keycloak:26.8.2";

    private static GenericContainer<?> container;

    private static KeycloakBroker broker;

    private final JsonMapper json = JsonMapper.builder().build();

    static boolean dockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    @BeforeAll
    static void start() {
        // Created here, not in a static field initializer: a skipped class must not need Docker.
        container = new GenericContainer<>(IMAGE)
                .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
                .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
                .withCopyFileToContainer(MountableFile.forHostPath(RealmFiles.directory().resolve("ff-logistics-realm.json")),
                        "/opt/keycloak/data/import/ff-logistics-realm.json")
                .withCommand("start-dev", "--import-realm")
                .withExposedPorts(8080)
                .waitingFor(Wait.forHttp("/realms/ff-logistics").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(3)));
        container.start();
        IdentityProperties properties = new IdentityProperties();
        IdentityProperties.Keycloak keycloak = properties.getKeycloak();
        keycloak.setBaseUrl("http://" + container.getHost() + ":" + container.getMappedPort(8080));
        keycloak.setRealm("ff-logistics");
        keycloak.setApiClientId("ff-logistics-api");
        keycloak.setServiceClientId("ff-logistics-service");
        keycloak.setServiceClientSecret("ff-logistics-service-secret");
        keycloak.setAdminUsername("admin");
        keycloak.setAdminPassword("admin");
        broker = new KeycloakBroker(properties, io.micrometer.observation.ObservationRegistry.NOOP);
    }

    @AfterAll
    static void stop() {
        if (container != null) {
            container.stop();
        }
    }

    @Test
    void aUserCreatedThroughTheBrokerCanSignInAndCarriesItsTenant() {
        broker.createUser("api.created", "api.created@ff-eu-freight.example", "Api-created-2026", "ff-eu-freight",
                List.of("LOGISTICS_PLANNER"), null, null);

        JsonNode claims = claims(broker.passwordGrant("api.created", "Api-created-2026"));

        assertThat(claims.path("tenant_id").asString()).isEqualTo("ff-eu-freight");
        assertThat(claims.path("realm_access").path("roles").toString()).contains("LOGISTICS_PLANNER");
        assertThat(claims.path("preferred_username").asString()).isEqualTo("api.created");
    }

    @Test
    void namesAreKeptWhenGiven() {
        broker.createUser("named.user", "named.user@ff-eu-freight.example", "Named-user-2026", "ff-eu-freight",
                List.of("REPORT_VIEWER"), "Ayse", "Yilmaz");

        JsonNode claims = claims(broker.passwordGrant("named.user", "Named-user-2026"));

        assertThat(claims.path("given_name").asString()).isEqualTo("Ayse");
        assertThat(claims.path("family_name").asString()).isEqualTo("Yilmaz");
        assertThat(claims.path("tenant_id").asString()).isEqualTo("ff-eu-freight");
    }

    @Test
    void theUsersOfTheRealmFileStillSignIn() {
        JsonNode claims = claims(broker.passwordGrant("nexus.planner", "fflogistics2026"));

        assertThat(claims.path("tenant_id").asString()).isEqualTo("ff-eu-freight");
    }

    @Test
    void theIntegrationClientsOfTheRealmFileActAsPlannersOfTheirTenant() {
        TokenResponse token = broker.clientCredentialsGrant("integration-ff-eu-freight.northfield",
                "integration-northfield-local-secret");
        JsonNode claims = claims(token);

        assertThat(token.refreshToken()).isNull();
        assertThat(claims.path("tenant_id").asString()).isEqualTo("ff-eu-freight");
        assertThat(claims.path("aud").toString()).contains("ff-logistics-api");
        assertThat(claims.path("realm_access").path("roles").toString()).isEqualTo("[\"LOGISTICS_PLANNER\"]");
        assertThat(claims(broker.clientCredentialsGrant("integration-ff-tr-distribution.corvane",
                "integration-corvane-local-secret")).path("tenant_id").asString()).isEqualTo("ff-tr-distribution");
    }

    @Test
    void anIntegrationClientCreatedThroughTheBrokerSignsInForItsTenant() {
        KeycloakBroker.IntegrationClient client = broker.createIntegrationClient("erp", "ff-tr-distribution");

        JsonNode claims = claims(broker.clientCredentialsGrant(client.clientId(), client.clientSecret()));

        assertThat(client.clientId()).isEqualTo("integration-ff-tr-distribution.erp");
        assertThat(claims.path("tenant_id").asString()).isEqualTo("ff-tr-distribution");
        assertThat(claims.path("aud").toString()).contains("ff-logistics-api");
        // like the realm file's clients: the planner role only, none of Keycloak's default roles
        assertThat(claims.path("realm_access").path("roles").toString()).isEqualTo("[\"LOGISTICS_PLANNER\"]");
        assertThat(claims.path("aud").toString()).doesNotContain("account");
    }

    @Test
    void theRealmFileDeclaresTheSameUserProfileAsUserProfileJson() throws Exception {
        JsonNode realm = json.readTree(Files.readString(RealmFiles.directory().resolve("ff-logistics-realm.json")));
        String embedded = realm.path("components").path("org.keycloak.userprofile.UserProfileProvider").get(0)
                .path("config").path("kc.user.profile.config").get(0).asString();
        JsonNode standalone = json.readTree(Files.readString(RealmFiles.directory().resolve("user-profile.json")));

        assertThat(json.readTree(embedded)).isEqualTo(standalone);
    }

    private JsonNode claims(TokenResponse token) {
        String payload = token.accessToken().split("\\.")[1];
        return json.readTree(new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8));
    }
}
