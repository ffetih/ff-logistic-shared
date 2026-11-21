package com.siose.ff.analytics.loader;

import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Writes deliveries into {@code shipment_deliveries} of the analytics database. */
@Repository
public class DeliveryStore {

    private static final String STORE = """
            with stored as (
                insert into shipment_deliveries (shipment_id, tenant_id, shipment_number, customer_order_reference,
                                                 total_distance_km, saved_co2e_kg, delivered_at)
                values (?, ?, ?, ?, ?, ?, ?)
                on conflict (shipment_id) do nothing
                returning tenant_id, delivered_at
            ), marked as (
                insert into delivery_month_changes (tenant_id, delivery_month)
                select tenant_id, %s from stored
                on conflict do nothing
            )
            select count(*) from stored
            """.formatted(DeliveryMonth.of("delivered_at"));

    private final JdbcTemplate jdbc;

    public DeliveryStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Stores the delivery once. Kafka delivers at least once, and an outbox may send a delivery
     * twice (it resends what it has not seen acknowledged), so a shipment that is already there is
     * left as it is.
     *
     * <p>In the same statement, a newly stored delivery marks its month as changed
     * ({@code delivery_month_changes}), so the rollup recounts that month. One statement: a delivery
     * is never stored without its mark. A delivery that was already there marks nothing.
     *
     * @return {@code true} when the row was written, {@code false} when it was already there
     */
    public boolean store(ShipmentDeliveredMessage delivery) {
        Integer rows = jdbc.queryForObject(STORE, Integer.class,
                delivery.shipmentId(), delivery.tenantId(), delivery.shipmentNumber(),
                delivery.customerOrderReference(), delivery.totalDistanceKm(), delivery.savedCo2eKg(),
                Timestamp.from(delivery.deliveredAt()));
        return rows != null && rows == 1;
    }
}
