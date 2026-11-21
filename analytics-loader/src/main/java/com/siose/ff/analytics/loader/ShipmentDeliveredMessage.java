package com.siose.ff.analytics.loader;

import com.siose.ff.base.tenant.TenantIds;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * One message of {@value #TOPIC}, as either implementation sends it ({@code ShipmentDelivered}
 * of the transportation area).
 *
 * <p>The loader's own reading of the JSON, not the producer's class: producer and loader share the
 * message format, not code. Fields the loader does not know are ignored, so the producer may add
 * fields; a field it needs that is missing or malformed makes the message invalid.
 */
public record ShipmentDeliveredMessage(
        UUID shipmentId,
        String tenantId,
        String shipmentNumber,
        String customerOrderReference,
        double totalDistanceKm,
        double savedCo2eKg,
        Instant deliveredAt) {

    public static final String TOPIC = "ff.logistics.tms.shipment-delivered";

    private static final int MAX_SHIPMENT_NUMBER = 40;
    private static final int MAX_ORDER_REFERENCE = 64;

    /** @throws InvalidMessageException naming the first thing wrong with the message */
    public static ShipmentDeliveredMessage parse(String json, JsonMapper mapper) {
        if (json == null || json.isBlank()) {
            throw new InvalidMessageException("Empty message");
        }
        JsonNode node;
        try {
            node = mapper.readTree(json);
        } catch (JacksonException e) {
            throw new InvalidMessageException("Not JSON", e);
        }
        if (node == null || !node.isObject()) {
            throw new InvalidMessageException("Not a JSON object");
        }

        String tenantId = text(node, "tenantId");
        if (!TenantIds.isValid(tenantId)) {
            throw new InvalidMessageException("tenantId is not a valid tenant id");
        }
        String shipmentNumber = text(node, "shipmentNumber");
        if (shipmentNumber.length() > MAX_SHIPMENT_NUMBER) {
            throw new InvalidMessageException("shipmentNumber is longer than " + MAX_SHIPMENT_NUMBER);
        }
        String reference = optionalText(node, "customerOrderReference");
        if (reference != null && reference.length() > MAX_ORDER_REFERENCE) {
            throw new InvalidMessageException("customerOrderReference is longer than " + MAX_ORDER_REFERENCE);
        }
        double distance = number(node, "totalDistanceKm");
        double saved = number(node, "savedCo2eKg");
        if (distance < 0 || saved < 0) {
            throw new InvalidMessageException("totalDistanceKm and savedCo2eKg must not be negative");
        }
        try {
            return new ShipmentDeliveredMessage(UUID.fromString(text(node, "shipmentId")), tenantId, shipmentNumber,
                    reference, distance, saved, Instant.parse(text(node, "deliveredAt")));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new InvalidMessageException("shipmentId or deliveredAt is malformed", e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isString() || value.asString().isBlank()) {
            throw new InvalidMessageException(field + " is missing");
        }
        return value.asString();
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isString()) {
            throw new InvalidMessageException(field + " is not text");
        }
        return value.asString().isBlank() ? null : value.asString();
    }

    private static double number(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber() || !Double.isFinite(value.asDouble())) {
            throw new InvalidMessageException(field + " is missing or not a number");
        }
        return value.asDouble();
    }
}
