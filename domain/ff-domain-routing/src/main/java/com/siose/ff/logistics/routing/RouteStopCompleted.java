package com.siose.ff.logistics.routing;

import java.time.Instant;
import java.util.UUID;
import org.springframework.modulith.events.Externalized;

/**
 * Published when the driver has handed over the goods at a stop and someone has signed for them.
 * Part of the routing area's API: other areas may listen to it, and see nothing else of routing.
 *
 * <p>It carries what a listener needs, the tenant included: a listener runs later, on another
 * thread (the modulith) or in another process (the services). {@code shipmentNumber} is the
 * shipment the stop delivers, {@code null} for a stop that delivers none of transportation's
 * shipments; transportation delivers it (flow 3).
 *
 * <p>{@code @Externalized}: with externalization on, the same event goes to Kafka as JSON, on
 * {@link #TOPIC}, keyed by the route plan, so the stops of one route stay in order on one
 * partition. Read by other systems and stored until handled, possibly across a release: add
 * fields, never rename or remove them.
 */
@Externalized(RouteStopCompleted.TOPIC + "::#{#this.routePlanId().toString()}")
public record RouteStopCompleted(
        UUID routePlanId,
        String tenantId,
        String routeNumber,
        String stopCode,
        String shipmentNumber,
        String receivedBy,
        Instant completedAt) {

    public static final String TOPIC = "ff.logistics.geo.route-stop-completed";
}
