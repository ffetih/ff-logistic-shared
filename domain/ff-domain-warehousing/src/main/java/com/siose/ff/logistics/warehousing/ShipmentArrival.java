package com.siose.ff.logistics.warehousing;

import java.time.Instant;
import java.util.UUID;

/**
 * A shipment that has arrived at a warehouse, in warehousing's own words: what this area needs to
 * open an inbound arrival, and nothing of how transportation describes a shipment. Part of the
 * area's API (its base package).
 *
 * <p>The implementations translate into it: the modulith from transportation's event in the same
 * JVM, the warehousing service from a Kafka message. So this area depends on no other one
 * (decision 2), and the same rules run on both sides.
 *
 * @param tenantId       whose shipment and warehouse; there is no request to take it from
 * @param shipmentId     transportation's id of the shipment; one arrival per shipment
 * @param shipmentNumber shown at the dock, such as {@code SHP-10001}
 * @param warehouseGln   the destination's GS1 location number: which warehouse
 * @param sscc           the pallet label to expect at the dock
 * @param grossWeightKg  what the dock should be ready for
 * @param arrivedAt      when the consignee signed
 */
public record ShipmentArrival(
        String tenantId,
        UUID shipmentId,
        String shipmentNumber,
        String warehouseGln,
        String sscc,
        double grossWeightKg,
        Instant arrivedAt) {
}
