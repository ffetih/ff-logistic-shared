package com.siose.ff.reporting.generation.internal;

import com.siose.ff.base.web.ResourceNotFoundException;
import com.siose.ff.reporting.AnalyticsSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reads {@code tenant_delivery_monthly} as {@code analytics_reader}. {@code set_config} is
 * local to the transaction, so the row-level policy sees the tenant and the next checkout does not.
 */
@Component
class MonthlyFiguresReader implements DisposableBean {

    private final HikariDataSource dataSource;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    MonthlyFiguresReader(AnalyticsSource source) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(source.url());
        config.setUsername(source.username());
        config.setPassword(source.password());
        config.setPoolName("analytics-reader");
        config.setMaximumPoolSize(2);
        // The pool is not a reason to refuse to start: a report is asked for later, and the
        // analytics database may be down while this process still serves its probes.
        config.setInitializationFailTimeout(-1);
        this.dataSource = new HikariDataSource(config);
        this.jdbc = new JdbcTemplate(dataSource);
        this.transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    MonthFigures read(String tenantId, LocalDate monthStart) {
        List<MonthFigures> rows = transactions.execute(status -> {
            jdbc.queryForObject("select set_config('app.tenant_id', ?, true)", String.class, tenantId);
            return jdbc.query("""
                    select delivered_shipments, total_distance_km, saved_co2e_kg
                    from tenant_delivery_monthly
                    where delivery_month = ?
                    """, (rs, row) -> new MonthFigures(rs.getInt(1), rs.getDouble(2), rs.getDouble(3)), monthStart);
        });
        if (rows == null || rows.size() != 1) {
            throw new ResourceNotFoundException("No monthly figures for " + monthStart);
        }
        return rows.getFirst();
    }

    @Override
    public void destroy() {
        dataSource.close();
    }
}
