package com.siose.ff.analytics.loader;

/** Tenant-months the recount wrote. Zero: nothing had changed since the last run. */
public record RollupView(int tenantMonths) {
}
