package com.siose.ff.logistics.warehousing.api;

import com.siose.ff.logistics.warehousing.service.WarehouseService;
import com.siose.ff.logistics.warehousing.service.WarehouseService.BinLocationView;
import com.siose.ff.logistics.warehousing.service.WarehouseService.NewBinLocation;
import com.siose.ff.logistics.warehousing.service.WarehouseService.NewWarehouse;
import com.siose.ff.logistics.warehousing.service.WarehouseService.WarehouseView;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wms/warehouses")
@PreAuthorize("hasAnyRole('WAREHOUSE_OPERATOR', 'TENANT_ADMIN')")
public class WarehouseController {

    private final WarehouseService service;

    public WarehouseController(WarehouseService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WarehouseView create(@Valid @RequestBody NewWarehouse request) {
        return service.create(request);
    }

    @GetMapping
    public List<WarehouseView> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public WarehouseView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/{id}/bins")
    @ResponseStatus(HttpStatus.CREATED)
    public BinLocationView addBin(@PathVariable UUID id, @Valid @RequestBody NewBinLocation request) {
        return service.addBin(id, request);
    }

    @GetMapping("/{id}/bins")
    public List<BinLocationView> bins(@PathVariable UUID id) {
        return service.bins(id);
    }
}
