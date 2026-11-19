/**
 * Logistics standards as pure functions: GS1 identifiers, ISO 6346 container codes, transport modes
 * and their EN 16258 / ISO 14083 emission factors, distances. No state, no Spring beans, no database:
 * every area may use it, and it uses none of them. For now only the transport modes; the rest is
 * ported in S3.
 */
@ApplicationModule(displayName = "Standards", allowedDependencies = {})
package com.siose.ff.logistics.standards;

import org.springframework.modulith.ApplicationModule;
