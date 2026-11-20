package com.siose.ff.logistics.warehousing.api;

import com.siose.ff.logistics.warehousing.service.ArrivalService;
import com.siose.ff.logistics.warehousing.service.ArrivalService.ArrivalView;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** What is at the docks. Opened by deliveries, not through this API: there is no POST. */
@RestController
@RequestMapping("/api/v1/wms/arrivals")
@PreAuthorize("hasAnyRole('WAREHOUSE_OPERATOR', 'TENANT_ADMIN')")
public class ArrivalController {

    private final ArrivalService service;

    public ArrivalController(ArrivalService service) {
        this.service = service;
    }

    @GetMapping
    public List<ArrivalView> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public ArrivalView get(@PathVariable UUID id) {
        return service.get(id);
    }
}
