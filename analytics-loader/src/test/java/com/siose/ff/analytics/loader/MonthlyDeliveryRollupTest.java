package com.siose.ff.analytics.loader;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The monthly figures: counted from the deliveries, per tenant and UTC month, only for the months
 * that changed, and right after a late delivery or a second run.
 */
@EnabledIf(AnalyticsTestSupport.CONDITION)
class MonthlyDeliveryRollupTest extends AnalyticsTestSupport {

    @Autowired
    private DeliveryStore store;

    @Autowired
    private MonthlyDeliveryRollup rollup;

    @Autowired
    private JdbcTemplate jdbc;

    private final String tenantA = "t-" + UUID.randomUUID().toString().substring(0, 8);
    private final String tenantB = "t-" + UUID.randomUUID().toString().substring(0, 8);

    @BeforeEach
    void nothingPending() {
        rollup.rollUp();
    }

    private ShipmentDeliveredMessage delivery(String tenant, String deliveredAt, double km, double co2e) {
        return new ShipmentDeliveredMessage(UUID.randomUUID(), tenant, "SHP-1", null, km, co2e,
                Instant.parse(deliveredAt));
    }

    private Map<String, Object> month(String tenant, String firstDay) {
        return jdbc.queryForMap("""
                select delivered_shipments, total_distance_km, saved_co2e_kg from tenant_delivery_monthly
                where tenant_id = ? and delivery_month = ?""", tenant, LocalDate.parse(firstDay));
    }

    private long pending(String tenant) {
        return jdbc.queryForObject("select count(*) from delivery_month_changes where tenant_id = ?", Long.class,
                tenant);
    }

    @Test
    void countsEachTenantAndMonthApart() {
        store.store(delivery(tenantA, "2026-10-03T08:00:00Z", 100, 10));
        store.store(delivery(tenantA, "2026-10-28T08:00:00Z", 50, 5));
        store.store(delivery(tenantA, "2026-11-02T08:00:00Z", 20, 2));
        store.store(delivery(tenantB, "2026-10-15T08:00:00Z", 7, 1));
        assertThat(pending(tenantA)).isEqualTo(2);

        assertThat(rollup.rollUp()).isEqualTo(3);

        assertThat(month(tenantA, "2026-10-01")).containsEntry("delivered_shipments", 2)
                .containsEntry("total_distance_km", 150.0).containsEntry("saved_co2e_kg", 15.0);
        assertThat(month(tenantA, "2026-11-01")).containsEntry("delivered_shipments", 1);
        assertThat(month(tenantB, "2026-10-01")).containsEntry("delivered_shipments", 1)
                .containsEntry("total_distance_km", 7.0);
        assertThat(pending(tenantA)).isZero();
    }

    @Test
    void aMonthIsAUtcMonth() {
        // 22:30 UTC on 31 October is already 1 November in Istanbul: it counts for October.
        store.store(delivery(tenantA, "2026-10-31T22:30:00Z", 1, 1));
        store.store(delivery(tenantA, "2026-11-01T00:00:00Z", 1, 1));
        rollup.rollUp();

        assertThat(month(tenantA, "2026-10-01")).containsEntry("delivered_shipments", 1);
        assertThat(month(tenantA, "2026-11-01")).containsEntry("delivered_shipments", 1);
    }

    @Test
    void recountsOnlyWhatChangedAndALateDeliveryComesOutRight() {
        store.store(delivery(tenantA, "2026-09-10T08:00:00Z", 10, 1));
        store.store(delivery(tenantA, "2026-11-10T08:00:00Z", 10, 1));
        rollup.rollUp();
        assertThat(rollup.rollUp()).as("nothing changed since").isZero();

        // A delivery for September, read weeks late: only September is recounted, from all its rows.
        store.store(delivery(tenantA, "2026-09-20T08:00:00Z", 30, 3));
        assertThat(rollup.rollUp()).isEqualTo(1);
        assertThat(month(tenantA, "2026-09-01")).containsEntry("delivered_shipments", 2)
                .containsEntry("total_distance_km", 40.0);
        assertThat(month(tenantA, "2026-11-01")).containsEntry("delivered_shipments", 1);
    }

    @Test
    void aDeliveryReadTwiceIsCountedOnceAndMarksNothing() {
        ShipmentDeliveredMessage delivery = delivery(tenantA, "2026-11-05T08:00:00Z", 10, 1);
        assertThat(store.store(delivery)).isTrue();
        rollup.rollUp();

        assertThat(store.store(delivery)).isFalse();
        assertThat(pending(tenantA)).isZero();
        assertThat(month(tenantA, "2026-11-01")).containsEntry("delivered_shipments", 1);
    }

    @Test
    void theRepairGivesTheSameFigures() {
        store.store(delivery(tenantA, "2026-08-01T08:00:00Z", 12.5, 2.5));
        store.store(delivery(tenantA, "2026-08-31T23:59:59Z", 12.5, 2.5));
        rollup.rollUp();
        Map<String, Object> routine = month(tenantA, "2026-08-01");

        rollup.rollUpAll();

        assertThat(month(tenantA, "2026-08-01")).isEqualTo(routine).containsEntry("delivered_shipments", 2);
        assertThat(pending(tenantA)).isZero();
    }
}
