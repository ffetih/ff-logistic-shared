package com.siose.ff.logistics.transportation.service;

import com.siose.ff.base.storage.DocumentLinks;
import com.siose.ff.base.storage.DocumentNotStoredException;
import com.siose.ff.base.storage.DocumentTransfer;
import com.siose.ff.base.storage.ObjectStoragePort;
import com.siose.ff.base.tenant.TenantContext;
import com.siose.ff.base.web.ResourceNotFoundException;
import com.siose.ff.logistics.transportation.domain.Shipment;
import com.siose.ff.logistics.transportation.domain.ShipmentRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The signed e-CMR of a shipment, as a PDF. The client sends it to the object store and fetches it
 * from there, through links this service signs; the bytes never pass through the application.
 * Written once: a stored e-CMR cannot be replaced (ff-base-storage's {@link DocumentLinks}).
 */
@Service
public class ConsignmentNoteService {

    static final String CATEGORY = "ecmr";
    static final String FILE_NAME = "ecmr.pdf";
    static final String CONTENT_TYPE = "application/pdf";

    private final ShipmentRepository shipments;
    private final ObjectStoragePort storage;

    ConsignmentNoteService(ShipmentRepository shipments, ObjectStoragePort storage) {
        this.shipments = shipments;
        this.storage = storage;
    }

    /**
     * Reserves the shipment's location on the first call: {@code ecmr/<shipment>/ecmr.pdf} under
     * the tenant's prefix. Refused once the e-CMR is stored (409); 503 without an object store.
     */
    @Transactional
    public DocumentTransfer uploadLink(UUID shipmentId) {
        Shipment shipment = shipment(shipmentId);
        if (shipment.getEcmrDocumentUri() == null) {
            shipment.reserveConsignmentNote(storage.registerDocumentReference(TenantContext.requireTenantId(),
                    CATEGORY, shipment.getShipmentNumber(), FILE_NAME, CONTENT_TYPE).objectUri());
        }
        return DocumentLinks.upload(storage, TenantContext.requireTenantId(), shipment.getEcmrDocumentUri(),
                CONTENT_TYPE);
    }

    /** 404 until the e-CMR has been uploaded. */
    @Transactional(readOnly = true)
    public DocumentTransfer downloadLink(UUID shipmentId) {
        Shipment shipment = shipment(shipmentId);
        if (shipment.getEcmrDocumentUri() == null) {
            throw new DocumentNotStoredException("This document has not been uploaded yet");
        }
        return DocumentLinks.download(storage, TenantContext.requireTenantId(), shipment.getEcmrDocumentUri(),
                CONTENT_TYPE);
    }

    private Shipment shipment(UUID id) {
        return shipments.findById(id).orElseThrow(() -> new ResourceNotFoundException("Shipment " + id + " not found"));
    }
}
