package com.siose.ff.logistics.warehousing.api;

import com.siose.ff.logistics.warehousing.service.AsnService;
import com.siose.ff.logistics.warehousing.service.AsnService.AsnView;
import com.siose.ff.logistics.warehousing.service.AsnService.NewAsn;
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
@RequestMapping("/api/v1/wms/asns")
@PreAuthorize("hasAnyRole('WAREHOUSE_OPERATOR', 'TENANT_ADMIN')")
public class AsnController {

    private final AsnService service;

    public AsnController(AsnService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AsnView create(@Valid @RequestBody NewAsn request) {
        return service.create(request);
    }

    @GetMapping
    public List<AsnView> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public AsnView get(@PathVariable UUID id) {
        return service.get(id);
    }
}
