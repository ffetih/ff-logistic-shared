package com.siose.ff.logistics.warehousing.api;

import com.siose.ff.logistics.warehousing.service.PalletizingService;
import com.siose.ff.logistics.warehousing.service.PalletizingService.NewPallet;
import com.siose.ff.logistics.warehousing.service.PalletizingService.PalletView;
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
@RequestMapping("/api/v1/wms")
@PreAuthorize("hasAnyRole('WAREHOUSE_OPERATOR', 'TENANT_ADMIN')")
public class PalletController {

    private final PalletizingService service;

    public PalletController(PalletizingService service) {
        this.service = service;
    }

    /** One pallet built from an ASN line and put away; the answer carries its SSCC. */
    @PostMapping("/asns/{asnId}/pallets")
    @ResponseStatus(HttpStatus.CREATED)
    public PalletView palletize(@PathVariable UUID asnId, @Valid @RequestBody NewPallet request) {
        return service.palletize(asnId, request);
    }

    @GetMapping("/asns/{asnId}/pallets")
    public List<PalletView> palletsOf(@PathVariable UUID asnId) {
        return service.palletsOf(asnId);
    }

    /** What a scanner asks when it reads a pallet label. */
    @GetMapping("/pallets/{sscc}")
    public PalletView bySscc(@PathVariable String sscc) {
        return service.bySscc(sscc);
    }
}
