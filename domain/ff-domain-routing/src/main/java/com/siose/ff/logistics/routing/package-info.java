/**
 * Routing: delivery territories, and route plans that put a vehicle's stops in driving order, with
 * time windows, a distance and an arrival time for each.
 *
 * <p>A domain core: the same code runs in the modulith and in the routing service. It uses
 * {@code standards} and nothing else of the business directly.
 *
 * <p>Its API is this package: {@link com.siose.ff.logistics.routing.RoadDistances}, the road distance
 * between two points, which transportation asks for its road legs (flow 1), and the event
 * {@link com.siose.ff.logistics.routing.RouteStopCompleted}, from which transportation delivers the
 * stop's shipment (flow 3).
 *
 * <p>{@code api} holds the REST controllers (under {@code /api/v1/geo}), {@code domain} the entities
 * and repositories, {@code planning} the ordering and the road distances (nearest neighbour, or
 * Timefold over a road matrix from OSRM or the straight-line estimate), {@code service} the use
 * cases. All four are internal. The tables keep the short name, {@code geo_*}.
 */
@ApplicationModule(displayName = "Routing", allowedDependencies = "standards")
package com.siose.ff.logistics.routing;

import org.springframework.modulith.ApplicationModule;
