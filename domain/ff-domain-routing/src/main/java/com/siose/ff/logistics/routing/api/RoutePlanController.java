package com.siose.ff.logistics.routing.api;

import com.siose.ff.base.storage.DocumentTransfer;
import com.siose.ff.logistics.routing.service.ProofOfDeliveryService;
import com.siose.ff.logistics.routing.service.RoutePlanService;
import com.siose.ff.logistics.routing.service.RoutePlanService.NewRoute;
import com.siose.ff.logistics.routing.service.RoutePlanService.RoutePlanView;
import com.siose.ff.logistics.routing.service.RoutePlanService.StopCompletion;
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
    private final ProofOfDeliveryService proofs;

    public RoutePlanController(RoutePlanService service, ProofOfDeliveryService proofs) {
        this.service = service;
        this.proofs = proofs;
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

    /**
     * The goods were handed over at a stop and signed for. 409 {@code stop-status-conflict} if the
     * stop was completed before; its shipment, if it names one, is delivered (flow 3).
     */
    @PostMapping("/{id}/stops/{stopCode}/completion")
    public RoutePlanView completeStop(@PathVariable UUID id, @PathVariable String stopCode,
            @Valid @RequestBody StopCompletion completion) {
        return service.completeStop(id, stopCode, completion);
    }

    /**
     * Where the client PUTs the stop's proof of delivery (the signature, PNG), with the headers the
     * answer names; valid 15 minutes. Refused with 409 once it is stored: documents are written once.
     */
    @PostMapping("/{id}/stops/{stopCode}/epod/upload-link")
    public DocumentTransfer epodUploadLink(@PathVariable UUID id, @PathVariable String stopCode) {
        return proofs.uploadLink(id, stopCode);
    }

    /** Where the client GETs the stop's proof of delivery; 404 until it is uploaded. */
    @GetMapping("/{id}/stops/{stopCode}/epod/download-link")
    public DocumentTransfer epodDownloadLink(@PathVariable UUID id, @PathVariable String stopCode) {
        return proofs.downloadLink(id, stopCode);
    }
}
