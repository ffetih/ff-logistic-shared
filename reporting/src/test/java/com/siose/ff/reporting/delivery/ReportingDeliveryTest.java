package com.siose.ff.reporting.delivery;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.siose.ff.reporting.ReportingApplication;
import com.siose.ff.reporting.generation.ReportReady;
import com.sun.net.httpserver.HttpServer;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

/**
 * A report is mailed and posted once. A second copy of the same event does not send again. A
 * webhook that keeps failing is tried again and then lands on the dead-letter topic; the mail,
 * already accepted, is not sent twice.
 */
@SpringBootTest(classes = ReportingApplication.class, properties = {
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.admin.auto-create=true",
        "delivery.retry-delay=PT0.2S",
        "delivery.retry-attempts=2",
        "ff.kafka.partitions=1",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration"
})
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1, topics = {
        ReportReady.TOPIC,
        ReportReady.TOPIC + KafkaErrorHandling.DEAD_LETTER_SUFFIX
})
@Import(ReportingDeliveryTest.Mail.class)
class ReportingDeliveryTest {

    private static final String TENANT = "ff-eu-freight";
    private static final String DLT = ReportReady.TOPIC + KafkaErrorHandling.DEAD_LETTER_SUFFIX;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KafkaTemplate<Object, Object> kafka;

    @Autowired
    private JsonMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Inbox inbox;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Test
    void savesTheChannelsOfTheSignedInTenant() throws Exception {
        mockMvc.perform(get("/api/v1/reports/channels")).andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/v1/reports/channels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"emailEnabled":true,"webhookEnabled":false}
                                """)
                        .with(planner()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/v1/reports/channels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"emailEnabled":true,"emailAddress":"planner@ff-eu-freight.test",
                                 "webhookEnabled":true,"webhookUrl":"http://hooks/report"}
                                """)
                        .with(planner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailAddress").value("planner@ff-eu-freight.test"))
                .andExpect(jsonPath("$.webhookUrl").value("http://hooks/report"));

        mockMvc.perform(get("/api/v1/reports/channels").with(planner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailEnabled").value(true))
                .andExpect(jsonPath("$.webhookEnabled").value(true));
    }

    @Test
    void sendsOnceAndSkipsTheMailWhenTheWebhookIsTriedAgain() throws Exception {
        inbox.messages.clear();
        AtomicInteger hits = new AtomicInteger();
        try (Hook hook = hook(hits, 1)) {
            channels(hook.url());
            ReportReady report = report();
            publish(report);
            await().atMost(Duration.ofSeconds(10)).until(() -> inbox.messages.size() == 1 && hits.get() == 2);
            assertThat(inbox.messages.getFirst()).contains("ff-eu-freight", "2026-11", "40", "1200.500", "80.250");
            publish(report);
            await().during(Duration.ofMillis(600)).atMost(Duration.ofSeconds(3))
                    .until(() -> inbox.messages.size() == 1 && hits.get() == 2);
            Integer sent = jdbc.queryForObject(
                    "select count(*) from delivery.sent_delivery where report_id = ?", Integer.class, report.reportId());
            assertThat(sent).isEqualTo(2);
        }
    }

    @Test
    void retriesAFailingWebhookAndThenDeadLettersIt() throws Exception {
        inbox.messages.clear();
        AtomicInteger hits = new AtomicInteger();
        try (Hook hook = hook(hits, Integer.MAX_VALUE)) {
            channels(hook.url(), false);
            ReportReady report = report();
            publish(report);
            await().atMost(Duration.ofSeconds(10)).until(() -> hits.get() >= 3);
            try (Consumer<String, String> consumer = dlt()) {
                var records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));
                assertThat(records.records(DLT)).anyMatch(record -> report.reportId().toString().equals(record.key()));
            }
            Integer sent = jdbc.queryForObject(
                    "select count(*) from delivery.sent_delivery where report_id = ?", Integer.class, report.reportId());
            assertThat(sent).isZero();
            assertThat(inbox.messages).isEmpty();
        }
    }

    private void channels(String webhook) throws Exception {
        channels(webhook, true);
    }

    private void channels(String webhook, boolean email) throws Exception {
        String body = email
                ? "{\"emailEnabled\":true,\"emailAddress\":\"planner@ff-eu-freight.test\",\"webhookEnabled\":true,\"webhookUrl\":\"%s\"}"
                : "{\"emailEnabled\":false,\"webhookEnabled\":true,\"webhookUrl\":\"%s\"}";
        mockMvc.perform(put("/api/v1/reports/channels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted(webhook))
                        .with(planner()))
                .andExpect(status().isOk());
    }

    private Consumer<String, String> dlt() {
        Map<String, Object> props = KafkaTestUtils.consumerProps(broker, "dlt-" + UUID.randomUUID(), false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(),
                new StringDeserializer()).createConsumer();
        consumer.subscribe(List.of(DLT));
        return consumer;
    }

    private void publish(ReportReady report) throws Exception {
        byte[] value = json.writeValueAsString(report).getBytes(StandardCharsets.UTF_8);
        kafka.send(ReportReady.TOPIC, report.reportId().toString(), value).get(5, SECONDS);
    }

    private static ReportReady report() {
        return new ReportReady(UUID.randomUUID(), TENANT, "2026-11",
                TENANT + "/report/2026-11/monthly.pdf", 40, 1200.5, 80.25, Instant.parse("2026-11-22T17:06:00Z"));
    }

    private static RequestPostProcessor planner() {
        return jwt().jwt(token -> token.subject("planner").claim("tenant_id", TENANT))
                .authorities(new SimpleGrantedAuthority("ROLE_LOGISTICS_PLANNER"));
    }

    /** {@code failures} first calls answer 500; the ones after answer 204. */
    private static Hook hook(AtomicInteger hits, int failures) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/report", exchange -> {
            exchange.getRequestBody().readAllBytes();
            int n = hits.incrementAndGet();
            if (n <= failures) {
                byte[] body = "no".getBytes();
                exchange.sendResponseHeaders(500, body.length);
                exchange.getResponseBody().write(body);
            } else {
                exchange.sendResponseHeaders(204, -1);
            }
            exchange.close();
        });
        server.start();
        return new Hook(server);
    }

    private record Hook(HttpServer server) implements AutoCloseable {
        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/report";
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    @TestConfiguration
    static class Mail {
        @Bean
        Inbox inbox() {
            return new Inbox();
        }
    }

    static class Inbox extends JavaMailSenderImpl {
        final List<String> messages = new CopyOnWriteArrayList<>();

        @Override
        public void send(MimeMessage mimeMessage) {
            try {
                messages.add(String.valueOf(mimeMessage.getContent()));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
