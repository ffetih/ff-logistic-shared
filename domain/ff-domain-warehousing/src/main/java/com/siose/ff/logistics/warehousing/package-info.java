/**
 * Warehousing: warehouses and bins, advance shipping notices, receiving and palletizing.
 *
 * <p>A domain core: the same code runs in the modulith and in the warehousing service. It uses
 * {@code standards} and nothing else of the business directly.
 */
@ApplicationModule(displayName = "Warehousing", allowedDependencies = "standards")
package com.siose.ff.logistics.warehousing;

import org.springframework.modulith.ApplicationModule;
