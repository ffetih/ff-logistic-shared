package com.siose.ff.logistics.routing.api;

import com.siose.ff.logistics.routing.service.RoutePlanService;
import com.siose.ff.logistics.routing.service.RoutePlanService.NewRoute;
import com.siose.ff.logistics.routing.service.RoutePlanService.RoutePlanView;
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
@RequestMapping("/api/v1/geo/routes")
@PreAuthorize("hasAnyRole('DISPATCHER', 'TENANT_ADMIN')")
public class RoutePlanController {

    private final RoutePlanService service;

    public RoutePlanController(RoutePlanService service) {
        this.service = service;
    }

    /** Orders the stops and numbers the plan; the answer has the stops in driving order. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoutePlanView plan(@Valid @RequestBody NewRoute request) {
        return service.plan(request);
    }

    @GetMapping
    public List<RoutePlanView> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public RoutePlanView get(@PathVariable UUID id) {
        return service.get(id);
    }
}
