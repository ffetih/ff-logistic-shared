package com.siose.ff.analytics.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** The loader's reading of a message, field by field, without broker or database. */
class ShipmentDeliveredMessageTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    /** A message as either implementation sends it (ShipmentDelivered), with one field it does not need. */
    static String json(String shipmentId, String tenantId, String deliveredAt) {
        return """
                {"shipmentId": "%s", "tenantId": "%s", "shipmentNumber": "SHP-10042",
                 "customerOrderReference": "PO-4711", "totalDistanceKm": 2580.0, "savedCo2eKg": 1912.9,
                 "deliveredAt": "%s", "aFieldAddedLater": true}""".formatted(shipmentId, tenantId, deliveredAt);
    }

    @Test
    void readsWhatTheLogisticsAppSends() {
        UUID id = UUID.randomUUID();

        ShipmentDeliveredMessage message = ShipmentDeliveredMessage.parse(
                json(id.toString(), "ff-eu-freight", "2026-11-19T10:15:30Z"), mapper);

        assertThat(message).isEqualTo(new ShipmentDeliveredMessage(id, "ff-eu-freight", "SHP-10042", "PO-4711",
                2580.0, 1912.9, Instant.parse("2026-11-19T10:15:30Z")));
    }

    @Test
    void anOrderReferenceIsOptional() {
        String withoutReference = json(UUID.randomUUID().toString(), "ff-eu-freight", "2026-11-19T10:15:30Z")
                .replace("\"PO-4711\"", "null");

        assertThat(ShipmentDeliveredMessage.parse(withoutReference, mapper).customerOrderReference()).isNull();
    }

    @Test
    void refusesWhatCanNeverLoad() {
        String good = json(UUID.randomUUID().toString(), "ff-eu-freight", "2026-11-19T10:15:30Z");
        assertInvalid("", "Empty message");
        assertInvalid("not json", "Not JSON");
        assertInvalid("[1, 2]", "Not a JSON object");
        assertInvalid(good.replace("ff-eu-freight", "FF EU Freight"), "tenantId is not a valid tenant id");
        assertInvalid(good.replace("\"shipmentNumber\": \"SHP-10042\",", ""), "shipmentNumber is missing");
        assertInvalid(good.replace("2580.0", "\"far\""), "totalDistanceKm is missing or not a number");
        assertInvalid(good.replace("1912.9", "-1"), "totalDistanceKm and savedCo2eKg must not be negative");
        assertInvalid(good.replace("2026-11-19T10:15:30Z", "yesterday"), "shipmentId or deliveredAt is malformed");
    }

    private void assertInvalid(String json, String reason) {
        assertThatThrownBy(() -> ShipmentDeliveredMessage.parse(json, mapper))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage(reason);
    }
}
