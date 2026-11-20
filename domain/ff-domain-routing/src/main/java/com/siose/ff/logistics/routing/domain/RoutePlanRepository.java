package com.siose.ff.logistics.routing.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Every query is filtered to the caller's tenant by Hibernate ({@code @TenantId}). */
public interface RoutePlanRepository extends JpaRepository<RoutePlan, UUID> {

    List<RoutePlan> findAllByOrderByCreatedAtDesc();
}
