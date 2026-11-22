/**
 * Sends a generated report on the tenant's channels. It listens to generation's
 * {@link com.siose.ff.reporting.generation.ReportReady} and to nothing else of that module.
 */
@ApplicationModule(displayName = "Report delivery", allowedDependencies = "generation")
package com.siose.ff.reporting.delivery;

import org.springframework.modulith.ApplicationModule;
