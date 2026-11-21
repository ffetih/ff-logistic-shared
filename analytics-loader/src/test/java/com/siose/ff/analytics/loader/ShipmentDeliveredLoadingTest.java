package com.siose.ff.analytics.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;

/**
 * The whole path through a real broker and a real database: a message becomes one row, a repeated
 * message changes nothing, and a broken message goes to the dead-letter topic without holding up
 * the messages behind it.
 */
@EnabledIf(AnalyticsTestSupport.CONDITION)
class ShipmentDeliveredLoadingTest extends AnalyticsTestSupport {

    private static final String DEAD_LETTERS = ShipmentDeliveredMessage.TOPIC + KafkaErrorHandling.DEAD_LETTER_SUFFIX;

    @Autowired
    private KafkaTemplate<Object, Object> kafka;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Test
    void aDeliveryIsLoadedOnceEvenWhenItArrivesTwice() throws Exception {
        String id = UUID.randomUUID().toString();
        String message = ShipmentDeliveredMessageTest.json(id, "ff-eu-freight", "2026-11-19T10:15:30Z");

        kafka.send(ShipmentDeliveredMessage.TOPIC, id, message).get();
        kafka.send(ShipmentDeliveredMessage.TOPIC, id, message).get();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(rows(id)).isEqualTo(1));
        Map<String, Object> row = jdbc.queryForMap("""
                select tenant_id, shipment_number, customer_order_reference, total_distance_km, saved_co2e_kg,
                       delivered_at
                from shipment_deliveries where shipment_id = ?::uuid""", id);
        assertThat(row).containsEntry("tenant_id", "ff-eu-freight")
                .containsEntry("shipment_number", "SHP-10042")
                .containsEntry("customer_order_reference", "PO-4711")
                .containsEntry("total_distance_km", new BigDecimal("2580.000"))
                .containsEntry("saved_co2e_kg", new BigDecimal("1912.900"));
        assertThat(((Timestamp) row.get("delivered_at")).toInstant()).isEqualTo(Instant.parse("2026-11-19T10:15:30Z"));

        // One key, one partition: the second copy is read before the next message. Still one row.
        String next = UUID.randomUUID().toString();
        kafka.send(ShipmentDeliveredMessage.TOPIC, id,
                ShipmentDeliveredMessageTest.json(next, "ff-eu-freight", "2026-11-19T10:16:00Z")).get();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(rows(next)).isEqualTo(1));
        assertThat(rows(id)).isEqualTo(1);
    }

    @Test
    void aBrokenMessageGoesToTheDeadLetterTopicAndTheNextOneStillLoads() throws Exception {
        String key = "broken-" + UUID.randomUUID();
        kafka.send(ShipmentDeliveredMessage.TOPIC, key, "{\"tenantId\": \"Not A Tenant\"}").get();
        String id = UUID.randomUUID().toString();
        kafka.send(ShipmentDeliveredMessage.TOPIC, key,
                ShipmentDeliveredMessageTest.json(id, "ff-tr-distribution", "2026-11-19T11:00:00Z")).get();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(rows(id)).isEqualTo(1));
        ConsumerRecord<String, String> dead = readWithKey(DEAD_LETTERS, key);
        assertThat(dead.value()).isEqualTo("{\"tenantId\": \"Not A Tenant\"}");
        // Spring adds why, and where the message came from, as headers.
        assertThat(new String(dead.headers().lastHeader("kafka_dlt-exception-message").value(), StandardCharsets.UTF_8))
                .contains("tenantId is not a valid tenant id");
        assertThat(new String(dead.headers().lastHeader("kafka_dlt-original-topic").value(), StandardCharsets.UTF_8))
                .isEqualTo(ShipmentDeliveredMessage.TOPIC);
    }

    private int rows(String shipmentId) {
        Integer count = jdbc.queryForObject("select count(*) from shipment_deliveries where shipment_id = ?::uuid",
                Integer.class, shipmentId);
        return count == null ? 0 : count;
    }

    private ConsumerRecord<String, String> readWithKey(String topic, String key) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString(),
                ConsumerConfig.GROUP_ID_CONFIG, "dead-letter-test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(topic));
            Instant deadline = Instant.now().plusSeconds(20);
            while (Instant.now().isBefore(deadline)) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (key.equals(record.key())) {
                        return record;
                    }
                }
            }
        }
        throw new AssertionError("No message with key " + key + " on " + topic + " within 20 s");
    }
}
