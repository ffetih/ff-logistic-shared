package com.siose.ff.logistics.routing.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Every query is filtered to the caller's tenant by Hibernate ({@code @TenantId}). */
public interface TerritoryRepository extends JpaRepository<Territory, UUID> {

    boolean existsByTerritoryCode(String territoryCode);

    List<Territory> findAllByOrderByTerritoryCode();
}
