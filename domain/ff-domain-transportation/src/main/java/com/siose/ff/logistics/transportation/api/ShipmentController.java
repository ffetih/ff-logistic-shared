package com.siose.ff.logistics.transportation.api;

import com.siose.ff.logistics.transportation.service.ShipmentService;
import com.siose.ff.logistics.transportation.service.ShipmentService.Delivery;
import com.siose.ff.logistics.transportation.service.ShipmentService.NewShipment;
import com.siose.ff.logistics.transportation.service.ShipmentService.PlanResult;
import com.siose.ff.logistics.transportation.service.ShipmentService.ShipmentView;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tms/shipments")
@PreAuthorize("hasAnyRole('LOGISTICS_PLANNER', 'TENANT_ADMIN')")
public class ShipmentController {

    private final ShipmentService service;

    public ShipmentController(ShipmentService service) {
        this.service = service;
    }

    /**
     * 201 for a new shipment. 200 with the existing one when the customer order number is known
     * and the plan is the same: a client may repeat a request it got no answer to.
     */
    @PostMapping
    public ResponseEntity<ShipmentView> plan(@Valid @RequestBody NewShipment request) {
        PlanResult result = service.plan(request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.shipment());
    }

    /** The carrier has collected the goods. Refused once the shipment is further along. */
    @PostMapping("/{id}/pickup")
    public ShipmentView pickUp(@PathVariable UUID id) {
        return service.pickUp(id);
    }

    /** The first leg has left. May follow the pickup or skip it. */
    @PostMapping("/{id}/departure")
    public ShipmentView depart(@PathVariable UUID id) {
        return service.depart(id);
    }

    /** The consignee has signed. Once only: a second confirmation answers 409. */
    @PostMapping("/{id}/delivery")
    public ShipmentView deliver(@PathVariable UUID id, @Valid @RequestBody Delivery delivery) {
        return service.deliver(id, delivery);
    }

    @GetMapping
    public List<ShipmentView> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public ShipmentView get(@PathVariable UUID id) {
        return service.get(id);
    }
}
