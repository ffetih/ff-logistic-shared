package com.siose.ff.logistics.warehousing.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * No method takes a tenant: Hibernate adds {@code tenant_id = ?} to every query and to
 * {@code findById}, from the tenant of the current request.
 */
public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {

    List<Warehouse> findAllByOrderByCode();

    boolean existsByCode(String code);

    boolean existsByGln(String gln);
}
