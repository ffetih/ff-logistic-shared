package com.siose.ff.logistics.routing.service;

import com.siose.ff.base.storage.DocumentLinks;
import com.siose.ff.base.storage.DocumentNotStoredException;
import com.siose.ff.base.storage.DocumentTransfer;
import com.siose.ff.base.storage.ObjectStoragePort;
import com.siose.ff.base.tenant.TenantContext;
import com.siose.ff.base.web.ResourceNotFoundException;
import com.siose.ff.logistics.routing.domain.RoutePlan;
import com.siose.ff.logistics.routing.domain.RoutePlanRepository;
import com.siose.ff.logistics.routing.domain.RouteStop;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The proof of delivery of a route stop: the consignee's signature, as a PNG. The client sends it
 * to the object store and fetches it from there, through links this service signs; the bytes never
 * pass through the application. Written once: a stored signature cannot be replaced.
 */
@Service
public class ProofOfDeliveryService {

    static final String CATEGORY = "epod";
    static final String FILE_NAME = "signature.png";
    static final String CONTENT_TYPE = "image/png";

    private final RoutePlanRepository plans;
    private final ObjectStoragePort storage;

    ProofOfDeliveryService(RoutePlanRepository plans, ObjectStoragePort storage) {
        this.plans = plans;
        this.storage = storage;
    }

    /**
     * Reserves the stop's location on the first call: {@code epod/<route>-<stop>/signature.png}
     * under the tenant's prefix. Refused once the signature is stored (409); 503 without an object
     * store.
     */
    @Transactional
    public DocumentTransfer uploadLink(UUID routeId, String stopCode) {
        RoutePlan plan = plan(routeId);
        RouteStop stop = plan.stop(stopCode);
        if (stop.getEpodDocumentUri() == null) {
            stop.reserveProofOfDelivery(storage.registerDocumentReference(TenantContext.requireTenantId(), CATEGORY,
                    plan.getRouteNumber() + "-" + stop.getStopCode(), FILE_NAME, CONTENT_TYPE).objectUri());
        }
        return DocumentLinks.upload(storage, TenantContext.requireTenantId(), stop.getEpodDocumentUri(), CONTENT_TYPE);
    }

    /** 404 until the signature has been uploaded. */
    @Transactional(readOnly = true)
    public DocumentTransfer downloadLink(UUID routeId, String stopCode) {
        RouteStop stop = plan(routeId).stop(stopCode);
        if (stop.getEpodDocumentUri() == null) {
            throw new DocumentNotStoredException("This document has not been uploaded yet");
        }
        return DocumentLinks.download(storage, TenantContext.requireTenantId(), stop.getEpodDocumentUri(), CONTENT_TYPE);
    }

    private RoutePlan plan(UUID routeId) {
        return plans.findById(routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Route plan " + routeId + " not found"));
    }
}
