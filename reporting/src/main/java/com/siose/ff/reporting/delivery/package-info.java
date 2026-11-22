/**
 * Sends a generated report on the tenant's channels. It may listen to generation's
 * {@link com.siose.ff.reporting.generation.ReportReady} and to nothing else of that module.
 * The listener and the channels are the next slice of this process.
 */
@ApplicationModule(displayName = "Report delivery", allowedDependencies = "generation")
package com.siose.ff.reporting.delivery;

import org.springframework.modulith.ApplicationModule;
