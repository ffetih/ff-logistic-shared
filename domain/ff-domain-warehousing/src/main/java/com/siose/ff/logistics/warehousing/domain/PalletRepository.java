package com.siose.ff.logistics.warehousing.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PalletRepository extends JpaRepository<Pallet, UUID> {

    long countByBinLocationId(UUID binLocationId);

    List<Pallet> findAllByAsnIdOrderBySscc(UUID asnId);

    Optional<Pallet> findBySscc(String sscc);
}
