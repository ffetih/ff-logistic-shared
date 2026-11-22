package com.siose.ff.reporting.generation.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

record StoredReport(
        UUID id,
        String tenantId,
        LocalDate deliveryMonth,
        String objectKey,
        int deliveredShipments,
        double totalDistanceKm,
        double savedCo2eKg,
        Instant generatedAt) {
}
