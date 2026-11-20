package com.siose.ff.logistics.warehousing;

/**
 * Opens an inbound arrival at a warehouse when a shipment has been delivered there: the goods are
 * at the dock, waiting to be received (flow 2). Part of the area's API.
 *
 * <p>Called without a request: from a listener in the modulith, from a Kafka consumer in the
 * warehousing service. It runs as {@link ShipmentArrival#tenantId()} and leaves the caller's tenant
 * as it found it.
 *
 * <p>Idempotent: the same shipment arriving twice (an event delivered again, a message read twice)
 * opens one arrival. The database decides, with a unique key on the shipment.
 */
public interface InboundArrivals {

    enum Outcome {
        /** A new arrival is open at the warehouse. */
        OPENED,
        /** This shipment's arrival was opened before; nothing changed. */
        ALREADY_OPEN,
        /** The tenant has no warehouse with that GLN: not a delivery to one of its warehouses. */
        NOT_A_WAREHOUSE
    }

    Outcome open(ShipmentArrival arrival);
}
