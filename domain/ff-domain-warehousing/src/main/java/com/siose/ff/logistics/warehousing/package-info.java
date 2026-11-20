/**
 * Warehousing: warehouses and bins, advance shipping notices, receiving and palletizing.
 *
 * <p>A domain core: the same code runs in the modulith and in the warehousing service. It uses
 * {@code standards} and nothing else of the business directly.
 *
 * <p>{@code api} holds the REST controllers (under {@code /api/v1/wms}), {@code domain} the entities
 * and repositories, {@code service} the use cases. All three are internal to the module. The tables
 * keep the industry's short name, {@code wms_*}.
 */
@ApplicationModule(displayName = "Warehousing", allowedDependencies = "standards")
package com.siose.ff.logistics.warehousing;

import org.springframework.modulith.ApplicationModule;
