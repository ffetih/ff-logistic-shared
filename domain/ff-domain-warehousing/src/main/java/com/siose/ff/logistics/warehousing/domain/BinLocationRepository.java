package com.siose.ff.logistics.warehousing.domain;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface BinLocationRepository extends JpaRepository<BinLocation, UUID> {

    List<BinLocation> findAllByWarehouseIdOrderByCode(UUID warehouseId);

    boolean existsByWarehouseIdAndCode(UUID warehouseId, String code);

    /**
     * The bin with a row lock, so two pallets for its last free place cannot both get it. Always
     * locked after the ASN, never before, so two receipts cannot wait for each other forever.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BinLocation> findForPutawayByWarehouseIdAndCode(UUID warehouseId, String code);
}
