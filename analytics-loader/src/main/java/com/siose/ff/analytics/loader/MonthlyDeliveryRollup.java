package com.siose.ff.analytics.loader;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Rolls deliveries up into {@code tenant_delivery_monthly}: per tenant and month ({@link DeliveryMonth}),
 * the delivered shipments, their distance and the CO2e they saved.
 *
 * <p>A run recounts only the months marked in {@code delivery_month_changes}, so its cost follows
 * what arrived since the last run, not the whole history. One statement takes the marks and
 * recounts their months: a failed run leaves its marks for the next one, and two runs at the same
 * moment count each month once. A month is always recounted from all its deliveries, never added
 * to, so a late delivery for a month long closed comes out right, and running twice changes
 * nothing.
 *
 * <p>Runs when the loader has started, then on {@code analytics.rollup.cron} (every five minutes).
 * {@link #rollUpAll()} rebuilds every month: a repair, not the routine. Not in the {@code migrate}
 * profile.
 */
@Component
@Profile("!migrate")
public class MonthlyDeliveryRollup {

    private static final Logger log = LoggerFactory.getLogger(MonthlyDeliveryRollup.class);

    private static final String UPSERT = """
            on conflict (tenant_id, delivery_month) do update set
                delivered_shipments = excluded.delivered_shipments,
                total_distance_km   = excluded.total_distance_km,
                saved_co2e_kg       = excluded.saved_co2e_kg,
                refreshed_at        = excluded.refreshed_at
            """;

    // Left join: a month that no longer has deliveries comes back with zeros, not as it was.
    private static final String ROLL_UP_CHANGED = """
            with changed as (
                delete from delivery_month_changes
                returning tenant_id, delivery_month
            )
            insert into tenant_delivery_monthly
                (tenant_id, delivery_month, delivered_shipments, total_distance_km, saved_co2e_kg, refreshed_at)
            select c.tenant_id, c.delivery_month, count(d.shipment_id),
                   coalesce(sum(d.total_distance_km), 0), coalesce(sum(d.saved_co2e_kg), 0), ?
            from changed c
            left join shipment_deliveries d
                   on d.tenant_id = c.tenant_id
                  and d.delivered_at >= %s
                  and d.delivered_at < %s
            group by c.tenant_id, c.delivery_month
            """.formatted(DeliveryMonth.start("c.delivery_month"), DeliveryMonth.end("c.delivery_month"))
            + UPSERT;

    private static final String ROLL_UP_ALL = """
            with cleared as (
                delete from delivery_month_changes
            )
            insert into tenant_delivery_monthly
                (tenant_id, delivery_month, delivered_shipments, total_distance_km, saved_co2e_kg, refreshed_at)
            select tenant_id, %s, count(*), sum(total_distance_km), sum(saved_co2e_kg), ?
            from shipment_deliveries
            group by 1, 2
            """.formatted(DeliveryMonth.of("delivered_at"))
            + UPSERT;

    private final JdbcTemplate jdbc;
    private final Timer duration;
    private final Counter months;
    private final AtomicLong lastSuccess = new AtomicLong();

    public MonthlyDeliveryRollup(JdbcTemplate jdbc, MeterRegistry meters) {
        this.jdbc = jdbc;
        this.duration = Timer.builder("analytics.rollup.duration")
                .description("Time to recount the changed months").register(meters);
        this.months = Counter.builder("analytics.rollup.months")
                .description("Tenant-months recounted").register(meters);
        Gauge.builder("analytics.rollup.last.success", lastSuccess, AtomicLong::get)
                .description("When the monthly figures were last brought up to date (epoch seconds, 0 = never)")
                .baseUnit("seconds").register(meters);
        Gauge.builder("analytics.rollup.pending", this, MonthlyDeliveryRollup::pendingMonths)
                .description("Tenant-months marked as changed and not recounted yet").register(meters);
    }

    @EventListener(ApplicationReadyEvent.class)
    void atStartup() {
        try {
            rollUp();
        } catch (DataAccessException e) {
            // The loader must keep loading; the next scheduled run tries again, and the marks stay.
            log.warn("Monthly delivery rollup at startup failed: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "${analytics.rollup.cron:0 */5 * * * *}", zone = "UTC")
    void scheduled() {
        rollUp();
    }

    /** Recounts the months marked as changed; returns the number of tenant-months written. */
    public int rollUp() {
        return run(ROLL_UP_CHANGED, "changed");
    }

    /** Recounts every month from every delivery and clears the marks: a repair, not the routine. */
    public int rollUpAll() {
        return run(ROLL_UP_ALL, "all");
    }

    private int run(String sql, String which) {
        Instant now = Instant.now();
        int rows = duration.record(() -> jdbc.update(sql, Timestamp.from(now)));
        months.increment(rows);
        lastSuccess.set(now.getEpochSecond());
        if (rows > 0) {
            log.info("Monthly delivery rollup ({}) recounted {} tenant-months", which, rows);
        }
        return rows;
    }

    private double pendingMonths() {
        try {
            Long pending = jdbc.queryForObject("select count(*) from delivery_month_changes", Long.class);
            return pending == null ? Double.NaN : pending;
        } catch (DataAccessException e) {
            return Double.NaN;
        }
    }
}
