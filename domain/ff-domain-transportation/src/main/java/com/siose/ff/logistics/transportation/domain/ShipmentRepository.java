package com.siose.ff.logistics.transportation.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant-filtered by Hibernate, like every repository of the app. */
public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {

    List<Shipment> findAllByOrderByCreatedAtDesc();

    Optional<Shipment> findByCustomerOrderReference(String customerOrderReference);

    Optional<Shipment> findByShipmentNumber(String shipmentNumber);
}
