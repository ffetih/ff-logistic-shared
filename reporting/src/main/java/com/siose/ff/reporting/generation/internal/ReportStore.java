package com.siose.ff.reporting.generation.internal;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

/** The generation schema. A month is stored once per tenant. */
@Component
class ReportStore {

    private static final RowMapper<StoredReport> ROW = (rs, n) -> new StoredReport(
            rs.getObject("id", UUID.class),
            rs.getString("tenant_id"),
            rs.getDate("delivery_month").toLocalDate(),
            rs.getString("object_key"),
            rs.getInt("delivered_shipments"),
            rs.getDouble("total_distance_km"),
            rs.getDouble("saved_co2e_kg"),
            rs.getTimestamp("generated_at").toInstant());

    private final JdbcTemplate jdbc;

    ReportStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Optional<StoredReport> find(String tenantId, YearMonth month) {
        List<StoredReport> rows = jdbc.query("""
                select id, tenant_id, delivery_month, object_key, delivered_shipments,
                       total_distance_km, saved_co2e_kg, generated_at
                from generation.report
                where tenant_id = ? and delivery_month = ?
                """, ROW, tenantId, Date.valueOf(month.atDay(1)));
        return rows.stream().findFirst();
    }

    Optional<StoredReport> find(String tenantId, UUID id) {
        List<StoredReport> rows = jdbc.query("""
                select id, tenant_id, delivery_month, object_key, delivered_shipments,
                       total_distance_km, saved_co2e_kg, generated_at
                from generation.report
                where tenant_id = ? and id = ?
                """, ROW, tenantId, id);
        return rows.stream().findFirst();
    }

    List<StoredReport> list(String tenantId) {
        return jdbc.query("""
                select id, tenant_id, delivery_month, object_key, delivered_shipments,
                       total_distance_km, saved_co2e_kg, generated_at
                from generation.report
                where tenant_id = ?
                order by delivery_month desc
                """, ROW, tenantId);
    }

    /**
     * @return the new row, or empty when this tenant already has this month (the other transaction won)
     */
    Optional<StoredReport> insert(StoredReport report) {
        int inserted = jdbc.update("""
                insert into generation.report
                    (id, tenant_id, delivery_month, object_key, delivered_shipments,
                     total_distance_km, saved_co2e_kg, generated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (tenant_id, delivery_month) do nothing
                """, report.id(), report.tenantId(), Date.valueOf(report.deliveryMonth()), report.objectKey(),
                report.deliveredShipments(), report.totalDistanceKm(), report.savedCo2eKg(),
                Timestamp.from(report.generatedAt()));
        return inserted == 1 ? Optional.of(report) : Optional.empty();
    }

    static StoredReport newReport(UUID id, String tenantId, LocalDate month, String objectKey, MonthFigures figures,
            Instant generatedAt) {
        return new StoredReport(id, tenantId, month, objectKey, figures.deliveredShipments(),
                figures.totalDistanceKm(), figures.savedCo2eKg(), generatedAt);
    }
}
