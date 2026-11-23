package com.siose.ff.analytics.loader;

import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The recount the five-minute schedule already runs ({@link MonthlyDeliveryRollup#rollUp()}).
 * The monthly close calls this; the schedule is unchanged, and this is not a second formula.
 * Restricted to {@code LOGISTICS_PLANNER} and {@code TENANT_ADMIN}. Absent from the
 * {@code migrate} profile, where the rollup bean is absent too.
 */
@RestController
@RequestMapping("/api/v1/rollups")
@Profile("!migrate")
@PreAuthorize("hasAnyRole('LOGISTICS_PLANNER', 'TENANT_ADMIN')")
public class RollupController {

    private final MonthlyDeliveryRollup rollup;

    public RollupController(MonthlyDeliveryRollup rollup) {
        this.rollup = rollup;
    }

    @PostMapping
    public RollupView recount() {
        return new RollupView(rollup.rollUp());
    }
}
