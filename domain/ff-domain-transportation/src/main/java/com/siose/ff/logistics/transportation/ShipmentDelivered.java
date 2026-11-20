package com.siose.ff.logistics.transportation;

import java.time.Instant;
import java.util.UUID;
import org.springframework.modulith.events.Externalized;

/**
 * Published when the consignee has signed for a shipment. It lives in the area's base package,
 * so it is part of the transportation area's API: other modules may listen to it, and see nothing else of it.
 *
 * <p>It carries what a listener needs, so no listener has to read transportation's tables, and it
 * carries the tenant: a listener runs later, on another thread (the modulith) or in another process
 * (the services), where no request and so no tenant is bound. {@code destinationGln}, {@code sscc}
 * and {@code grossWeightKg} are for the warehouse at the destination, if there is one: it opens an
 * inbound arrival (flow 2). {@code destinationGln} is {@code null} for any other destination.
 *
 * <p>{@code @Externalized}: with externalization on, the same event also goes to Kafka as JSON, on
 * {@link #TOPIC}, keyed by the shipment id, so every message about one shipment lands on one
 * partition (arrival order is still not guaranteed: see {@link ShipmentProgressed}). Systems outside
 * this application read it, and it is stored as JSON
 * in {@code event_publication} until every listener has handled it, possibly across a release:
 * add fields, never rename or remove them.
 */
@Externalized(ShipmentDelivered.TOPIC + "::#{#this.shipmentId().toString()}")
public record ShipmentDelivered(
        UUID shipmentId,
        String tenantId,
        String shipmentNumber,
        String customerOrderReference,
        double totalDistanceKm,
        double savedCo2eKg,
        Instant deliveredAt,
        String destinationGln,
        String sscc,
        double grossWeightKg) {

    public static final String TOPIC = "ff.logistics.tms.shipment-delivered";
}
