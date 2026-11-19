/**
 * Routing: delivery territories and route plans with time windows, optimized over a road matrix, with proof of delivery.
 *
 * <p>A domain core: the same code runs in the modulith and in the routing service. It uses
 * {@code standards} and nothing else of the business directly.
 */
@ApplicationModule(displayName = "Routing", allowedDependencies = "standards")
package com.siose.ff.logistics.routing;

import org.springframework.modulith.ApplicationModule;
