package com.siose.ff.logistics.transportation;

/**
 * Delivers a shipment when a route stop that carried it has been completed: the goods were handed
 * over and signed for at the stop (flow 3). Part of the area's API.
 *
 * <p>Called without a request: from a listener in the modulith, from a Kafka consumer in the
 * transportation service. It runs as {@link StopDelivery#tenantId()} and leaves the caller's
 * tenant as it found it.
 *
 * <p>The delivery is the same as one made through the API: the shipment becomes DELIVERED, and
 * {@link ShipmentDelivered} is published, so a shipment delivered to a warehouse opens an arrival
 * there (flow 2). One completed stop can so move three areas.
 *
 * <p>Idempotent: a stop completion handled twice (an event delivered again, a message read twice)
 * delivers the shipment once. The shipment's version decides when two arrive at the same time.
 */
public interface ShipmentDeliveries {

    enum Outcome {
        /** The shipment is now delivered. */
        DELIVERED,
        /** It was delivered before; nothing changed. */
        ALREADY_DELIVERED,
        /** The tenant has no shipment with that number: nothing to deliver. */
        UNKNOWN_SHIPMENT
    }

    Outcome deliver(StopDelivery delivery);
}
