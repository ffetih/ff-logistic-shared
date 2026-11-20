package com.siose.ff.logistics.warehousing.service;

import com.siose.ff.base.web.ResourceNotFoundException;
import com.siose.ff.logistics.warehousing.domain.BinKind;
import com.siose.ff.logistics.warehousing.domain.BinLocation;
import com.siose.ff.logistics.warehousing.domain.BinLocationRepository;
import com.siose.ff.logistics.warehousing.domain.Warehouse;
import com.siose.ff.logistics.warehousing.domain.WarehouseRepository;
import com.siose.ff.logistics.warehousing.domain.WmsConflictException;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Warehouses and their bin locations, always those of the caller's tenant. */
@Service
public class WarehouseService {

    private final WarehouseRepository warehouses;
    private final BinLocationRepository bins;

    WarehouseService(WarehouseRepository warehouses, BinLocationRepository bins) {
        this.warehouses = warehouses;
        this.bins = bins;
    }

    public record NewWarehouse(
            @NotBlank @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9-]+") String code,
            @NotBlank @Size(max = 140) String name,
            @NotBlank String gln,
            @NotBlank String gs1CompanyPrefix,
            @NotBlank @Pattern(regexp = "[A-Za-z]{2}") String countryCode,
            @NotBlank @Size(max = 80) String city,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
            @NotNull @Min(1) Integer palletCapacity) {
    }

    public record WarehouseView(UUID id, String tenantId, String code, String name, String gln,
            String gs1CompanyPrefix, String countryCode, String city, double latitude, double longitude,
            int palletCapacity, Instant createdAt) {
    }

    public record NewBinLocation(
            @NotBlank @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9-]+") String code,
            @NotNull BinKind kind,
            @NotNull @Min(1) Integer palletCapacity) {
    }

    public record BinLocationView(UUID id, UUID warehouseId, String code, BinKind kind, int palletCapacity) {
    }

    @Transactional
    public WarehouseView create(NewWarehouse request) {
        Warehouse warehouse = new Warehouse(request.code(), request.name(), request.gln(),
                request.gs1CompanyPrefix(), request.countryCode(), request.city(), request.latitude(),
                request.longitude(), request.palletCapacity());
        if (warehouses.existsByCode(warehouse.getCode())) {
            throw WmsConflictException.warehouseCodeTaken(warehouse.getCode());
        }
        if (warehouses.existsByGln(warehouse.getGln())) {
            throw WmsConflictException.warehouseGlnTaken(warehouse.getGln());
        }
        return view(warehouses.save(warehouse));
    }

    @Transactional(readOnly = true)
    public List<WarehouseView> list() {
        return warehouses.findAllByOrderByCode().stream().map(WarehouseService::view).toList();
    }

    @Transactional(readOnly = true)
    public WarehouseView get(UUID id) {
        return view(warehouse(id));
    }

    @Transactional
    public BinLocationView addBin(UUID warehouseId, NewBinLocation request) {
        Warehouse warehouse = warehouse(warehouseId);
        BinLocation bin = new BinLocation(warehouse.getId(), request.code(), request.kind(), request.palletCapacity());
        if (bins.existsByWarehouseIdAndCode(warehouse.getId(), bin.getCode())) {
            throw WmsConflictException.binCodeTaken(bin.getCode());
        }
        return view(bins.save(bin));
    }

    @Transactional(readOnly = true)
    public List<BinLocationView> bins(UUID warehouseId) {
        Warehouse warehouse = warehouse(warehouseId);
        return bins.findAllByWarehouseIdOrderByCode(warehouse.getId()).stream().map(WarehouseService::view).toList();
    }

    /** Another tenant's warehouse is filtered out by Hibernate, so it is simply not found. */
    private Warehouse warehouse(UUID id) {
        return warehouses.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse " + id + " not found"));
    }

    private static WarehouseView view(Warehouse w) {
        return new WarehouseView(w.getId(), w.getTenantId(), w.getCode(), w.getName(), w.getGln(),
                w.getGs1CompanyPrefix(), w.getCountryCode(), w.getCity(), w.getLatitude(), w.getLongitude(),
                w.getPalletCapacity(), w.getCreatedAt());
    }

    private static BinLocationView view(BinLocation b) {
        return new BinLocationView(b.getId(), b.getWarehouseId(), b.getCode(), b.getKind(), b.getPalletCapacity());
    }
}
