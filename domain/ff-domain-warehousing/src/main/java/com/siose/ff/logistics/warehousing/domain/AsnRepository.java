package com.siose.ff.logistics.warehousing.domain;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface AsnRepository extends JpaRepository<Asn, UUID> {

    /** The receiving plan: what arrives first comes first. */
    List<Asn> findAllByOrderByExpectedArrivalAscAsnNumberAsc();

    boolean existsByAsnNumber(String asnNumber);

    /**
     * The ASN with a row lock ({@code SELECT ... FOR UPDATE}) until the transaction ends. Two
     * scanners receiving against the same ASN then take turns instead of both reading the same
     * open quantity and booking more than was announced.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Asn> findForReceivingById(UUID id);
}
