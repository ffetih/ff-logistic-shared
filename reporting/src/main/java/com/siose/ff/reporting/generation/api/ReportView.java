package com.siose.ff.reporting.generation.api;

import java.time.Instant;
import java.util.UUID;

/** One stored month, and a fresh download link for its PDF. */
public record ReportView(
        UUID id,
        String month,
        int deliveredShipments,
        double totalDistanceKm,
        double savedCo2eKg,
        Instant generatedAt,
        String downloadUrl) {
}
