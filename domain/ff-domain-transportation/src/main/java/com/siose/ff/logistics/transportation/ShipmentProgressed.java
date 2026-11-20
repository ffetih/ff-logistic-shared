package com.siose.ff.logistics.transportation;

import java.time.Instant;
import java.util.UUID;
import org.springframework.modulith.events.Externalized;

/**
 * Published when a shipment moves on before its delivery: collected at the origin, or departed on
 * its first leg. Part of the transportation area's API like {@link ShipmentDelivered}, and like it sent to Kafka when
 * externalization is on, keyed by the shipment id. Add fields, never rename or remove them.
 *
 * <p>The messages of one shipment share a partition, but they may still arrive out of order: each
 * event is sent on its own after its transaction commits, and a failed send is sent again later,
 * after the steps that followed it. A consumer orders the steps by {@code occurredAt} (a shipment
 * only moves forward), never by arrival.
 *
 * @param stage                  {@link #PICKED_UP} or {@link #DEPARTED}
 * @param occurredAt             when the step was recorded
 * @param customerOrderReference the customer's own order number, or {@code null}
 */
@Externalized(ShipmentProgressed.TOPIC + "::#{#this.shipmentId().toString()}")
public record ShipmentProgressed(
        UUID shipmentId,
        String tenantId,
        String shipmentNumber,
        String customerOrderReference,
        String stage,
        Instant occurredAt) {

    public static final String TOPIC = "ff.logistics.tms.shipment-progressed";

    public static final String PICKED_UP = "PICKED_UP";
    public static final String DEPARTED = "DEPARTED";
}
