/**
 * Transportation: shipments over multimodal legs, their lifecycle from pickup to delivery, the e-CMR and the CO2e footprint.
 *
 * <p>A domain core: the same code runs in the modulith and in the transportation service. It uses
 * {@code standards} and nothing else of the business directly.
 */
@ApplicationModule(displayName = "Transportation", allowedDependencies = "standards")
package com.siose.ff.logistics.transportation;

import org.springframework.modulith.ApplicationModule;
