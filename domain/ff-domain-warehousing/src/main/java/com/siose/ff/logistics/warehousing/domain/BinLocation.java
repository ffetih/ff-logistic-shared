package com.siose.ff.logistics.warehousing.domain;

import com.siose.ff.base.persistence.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * One addressable place in a warehouse, such as {@code A02-R11-L04} (aisle, rack, level).
 * The code is what is printed on the label at the place and what a scanner reads.
 */
@Entity
@Table(name = "wms_bin_locations")
public class BinLocation extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID warehouseId;

    @Column(nullable = false, length = 40)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BinKind kind;

    private int palletCapacity;

    protected BinLocation() {
    }

    public BinLocation(UUID warehouseId, String code, BinKind kind, int palletCapacity) {
        if (palletCapacity < 1) {
            throw new IllegalArgumentException("A bin location holds at least one pallet");
        }
        this.warehouseId = warehouseId;
        this.code = code.trim().toUpperCase();
        this.kind = kind;
        this.palletCapacity = palletCapacity;
    }

    public UUID getId() {
        return id;
    }

    public UUID getWarehouseId() {
        return warehouseId;
    }

    public String getCode() {
        return code;
    }

    public BinKind getKind() {
        return kind;
    }

    public int getPalletCapacity() {
        return palletCapacity;
    }
}
