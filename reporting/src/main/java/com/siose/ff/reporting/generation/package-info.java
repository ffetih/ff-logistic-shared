/**
 * Renders one tenant's monthly PDF from the analytics rollup and publishes {@link ReportReady}.
 * The PDF bytes stay in object storage; the event carries the key.
 */
@ApplicationModule(displayName = "Report generation")
package com.siose.ff.reporting.generation;

import org.springframework.modulith.ApplicationModule;
