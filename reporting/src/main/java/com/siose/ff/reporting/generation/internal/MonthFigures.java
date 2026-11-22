package com.siose.ff.reporting.generation.internal;

/** One tenant's month, as stored on {@code tenant_delivery_monthly}. No formula of our own. */
record MonthFigures(int deliveredShipments, double totalDistanceKm, double savedCo2eKg) {
}
