package com.siose.ff.logistics.warehousing.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Like every repository here, filtered to the current tenant by Hibernate. */
public interface InboundArrivalRepository extends JpaRepository<InboundArrival, UUID> {

    boolean existsByShipmentId(UUID shipmentId);

    List<InboundArrival> findAllByOrderByArrivedAtDesc();
}
