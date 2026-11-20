/**
 * Transportation: shipments over multimodal legs, their lifecycle from pickup to delivery, the e-CMR
 * number and the CO2e footprint.
 *
 * <p>A domain core: the same code runs in the modulith and in the transportation service. It uses
 * {@code standards} and nothing else of the business directly.
 *
 * <p>Its API is this package: the events {@link com.siose.ff.logistics.transportation.ShipmentProgressed}
 * and {@link com.siose.ff.logistics.transportation.ShipmentDelivered}, which other areas react to
 * through the implementation's wiring (an in-process listener, or Kafka), and the port
 * {@link com.siose.ff.logistics.transportation.DistanceLookup}, a question it asks routing (flow 1),
 * which the implementation answers (a method call, or HTTP). {@code api} holds the REST
 * controller (under {@code /api/v1/tms}), {@code domain} the entities and repositories,
 * {@code service} the use cases; all three are internal. The tables keep the short name,
 * {@code tms_*}.
 */
@ApplicationModule(displayName = "Transportation", allowedDependencies = "standards")
package com.siose.ff.logistics.transportation;

import org.springframework.modulith.ApplicationModule;
