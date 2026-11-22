package com.siose.ff.reporting.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.siose.ff.contracts.EventContract;
import com.siose.ff.reporting.ReportingApplication;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The PDF is the rollup's figures, and the other tenant's month is not in it. The reader login is
 * {@code analytics_reader}; the table forces row-level security, so a forgotten tenant filter still
 * cannot see the other row.
 */
@SpringBootTest(classes = ReportingApplication.class, properties = {
        "spring.modulith.events.externalization.enabled=true",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.admin.auto-create=true"
})
@AutoConfigureMockMvc
@EmbeddedKafka(topics = ReportReady.TOPIC)
@DirtiesContext
@Import(ReportingGenerationTest.Documents.class)
@EnabledIf("com.siose.ff.reporting.generation.ReportingGenerationTest#databaseAvailable")
class ReportingGenerationTest {

    private static final String EU = "ff-eu-freight";
    private static final String TR = "ff-tr-distribution";

    private static PostgreSQLContainer postgres;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Autowired
    private MemoryDocuments documents;

    static boolean databaseAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        start();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("analytics.datasource.url", postgres::getJdbcUrl);
        registry.add("analytics.datasource.username", () -> "analytics_reader");
        registry.add("analytics.datasource.password", () -> "analyticsReader2026");
    }

    @BeforeAll
    static void rollup() throws Exception {
        start();
        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    DO $$ BEGIN
                        IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'analytics_reader') THEN
                            CREATE ROLE analytics_reader LOGIN NOSUPERUSER NOBYPASSRLS PASSWORD 'analyticsReader2026';
                        END IF;
                    END $$
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS tenant_delivery_monthly (
                        tenant_id varchar(64) not null,
                        delivery_month date not null,
                        delivered_shipments integer not null,
                        total_distance_km double precision not null,
                        saved_co2e_kg double precision not null,
                        refreshed_at timestamptz not null,
                        primary key (tenant_id, delivery_month)
                    )
                    """);
            statement.execute("ALTER TABLE tenant_delivery_monthly ENABLE ROW LEVEL SECURITY");
            statement.execute("ALTER TABLE tenant_delivery_monthly FORCE ROW LEVEL SECURITY");
            statement.execute("DROP POLICY IF EXISTS tenant_isolation ON tenant_delivery_monthly");
            statement.execute("""
                    CREATE POLICY tenant_isolation ON tenant_delivery_monthly
                        USING (tenant_id = current_setting('app.tenant_id', true)
                               OR current_setting('app.all_tenants', true) = 'on')
                    """);
            statement.execute("GRANT SELECT ON tenant_delivery_monthly TO analytics_reader");
            // The cluster's V2 revokes this. SET LOCAL still works; a query must not call set_config.
            statement.execute(
                    "REVOKE EXECUTE ON FUNCTION pg_catalog.set_config(text, text, boolean) FROM PUBLIC");
            statement.execute("DELETE FROM tenant_delivery_monthly");
            statement.execute("""
                    INSERT INTO tenant_delivery_monthly
                        (tenant_id, delivery_month, delivered_shipments, total_distance_km, saved_co2e_kg, refreshed_at)
                    VALUES
                        ('ff-eu-freight', DATE '2026-11-01', 7412, 88067.350, 7772.400, now()),
                        ('ff-tr-distribution', DATE '2026-11-01', 15, 100.000, 8.000, now())
                    """);
        }
    }

    @Test
    void aTenantDownloadsItsOwnMonthAndCannotReadTheOther() throws Exception {
        String eu = monthly(EU, 201);
        assertThat((Integer) JsonPath.read(eu, "$.deliveredShipments")).isEqualTo(7412);
        assertThat(text(eu)).contains("ff-eu-freight", "2026-11", "7412", "88067.350", "7772.400");
        assertThat(text(eu)).doesNotContain("100.000");

        String again = monthly(EU, 200);
        assertThat((String) JsonPath.read(again, "$.id")).isEqualTo(JsonPath.read(eu, "$.id"));

        String tr = monthly(TR, 201);
        assertThat((Integer) JsonPath.read(tr, "$.deliveredShipments")).isEqualTo(15);
        assertThat(text(tr)).contains("15", "100.000", "8.000");
        assertThat(text(tr)).doesNotContain("7412");

        mockMvc.perform(get("/api/v1/reports/" + JsonPath.read(eu, "$.id")).with(caller(TR)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/reports").with(caller(TR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].deliveredShipments").value(15));

        java.util.List<ConsumerRecord<String, String>> events = records();
        String euId = JsonPath.read(eu, "$.id");
        ConsumerRecord<String, String> euEvent = one(events, euId);
        EventContract.of(ReportReady.TOPIC).verify(euEvent.key(), euEvent.value());
        assertThat((String) JsonPath.read(euEvent.value(), "$.tenantId")).isEqualTo(EU);
        assertThat((String) JsonPath.read(euEvent.value(), "$.objectKey")).contains("ff-eu-freight/report/2026-11/");

        String trId = JsonPath.read(tr, "$.id");
        ConsumerRecord<String, String> trEvent = one(events, trId);
        EventContract.of(ReportReady.TOPIC).verify(trEvent.key(), trEvent.value());
        assertThat(events).hasSize(2);
    }

    private static ConsumerRecord<String, String> one(java.util.List<ConsumerRecord<String, String>> events, String id) {
        java.util.List<ConsumerRecord<String, String>> matched = events.stream()
                .filter(record -> id.equals(record.key())).toList();
        assertThat(matched).hasSize(1);
        return matched.getFirst();
    }

    private String monthly(String tenant, int status) throws Exception {
        return mockMvc.perform(post("/api/v1/reports/monthly")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"2026-11\"}")
                        .with(caller(tenant)))
                .andExpect(status().is(status))
                .andReturn().getResponse().getContentAsString();
    }

    private String text(String body) throws Exception {
        String url = JsonPath.read(body, "$.downloadUrl");
        byte[] pdf = documents.bytes(url.substring("memory://".length()));
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private java.util.List<ConsumerRecord<String, String>> records() {
        try (Consumer<String, String> consumer = consumer()) {
            return java.util.stream.StreamSupport.stream(
                    KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(15)).records(ReportReady.TOPIC).spliterator(),
                    false).toList();
        }
    }

    private Consumer<String, String> consumer() {
        Map<String, Object> props = KafkaTestUtils.consumerProps(broker, "report-ready-" + UUID.randomUUID(), false);
        props.put("auto.offset.reset", "earliest");
        Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(),
                new StringDeserializer()).createConsumer();
        consumer.subscribe(java.util.List.of(ReportReady.TOPIC));
        return consumer;
    }

    private static RequestPostProcessor caller(String tenant) {
        return jwt().jwt(token -> token.subject("planner").claim("tenant_id", tenant))
                .authorities(new SimpleGrantedAuthority("ROLE_LOGISTICS_PLANNER"));
    }

    private static void start() {
        if (postgres == null) {
            postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
            postgres.start();
        }
    }

    @TestConfiguration
    static class Documents {
        @Bean
        MemoryDocuments documents() {
            return new MemoryDocuments("ff-documents");
        }
    }
}
