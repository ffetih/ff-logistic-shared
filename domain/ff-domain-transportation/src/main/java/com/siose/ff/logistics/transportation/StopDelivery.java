package com.siose.ff.logistics.transportation;

import java.time.Instant;

/**
 * A completed route stop, as transportation needs it (flow 3): which shipment, who signed, when,
 * and at which stop of which route, for the log.
 */
public record StopDelivery(String tenantId, String shipmentNumber, String receivedBy, Instant deliveredAt,
        String routeNumber, String stopCode) {
}
