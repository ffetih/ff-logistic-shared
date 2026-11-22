package com.siose.ff.reporting.generation;

import java.time.Instant;
import java.util.UUID;
import org.springframework.modulith.events.Externalized;

/**
 * A monthly PDF is in object storage. {@code objectKey} is the claim check: the bytes are not in
 * this event. {@code month} is {@code yyyy-MM}.
 *
 * <p>{@code @Externalized}: the same event goes to Kafka as JSON, on {@link #TOPIC}, keyed by the
 * report id. Delivery, in this process, consumes it from the broker so the trace crosses that hop.
 */
@Externalized(ReportReady.TOPIC + "::#{#this.reportId().toString()}")
public record ReportReady(
        UUID reportId,
        String tenantId,
        String month,
        String objectKey,
        int deliveredShipments,
        double totalDistanceKm,
        double savedCo2eKg,
        Instant generatedAt) {

    public static final String TOPIC = "ff.logistics.reporting.report-ready";
}
