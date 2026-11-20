package com.siose.ff.logistics.routing.api;

import com.siose.ff.logistics.routing.service.TerritoryService;
import com.siose.ff.logistics.routing.service.TerritoryService.NewTerritory;
import com.siose.ff.logistics.routing.service.TerritoryService.TerritoryView;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/geo/territories")
@PreAuthorize("hasAnyRole('DISPATCHER', 'TENANT_ADMIN')")
public class TerritoryController {

    private final TerritoryService service;

    public TerritoryController(TerritoryService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TerritoryView create(@Valid @RequestBody NewTerritory request) {
        return service.create(request);
    }

    @GetMapping
    public List<TerritoryView> list() {
        return service.list();
    }
}
