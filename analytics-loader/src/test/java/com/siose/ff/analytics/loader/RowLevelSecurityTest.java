package com.siose.ff.analytics.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * What {@code analytics_reader} may see and do. The loader (the test's own connection) writes rows
 * of two tenants; the reader connects with its own password, as a dashboard will.
 */
@EnabledIf(AnalyticsTestSupport.CONDITION)
class RowLevelSecurityTest extends AnalyticsTestSupport {

    @Autowired
    private JdbcTemplate loader;

    @Value("${spring.datasource.url}")
    private String url;

    @Value("${spring.flyway.placeholders.analytics_reader_password}")
    private String readerPassword;

    private final String tenantA = "t-" + UUID.randomUUID().toString().substring(0, 8);
    private final String tenantB = "t-" + UUID.randomUUID().toString().substring(0, 8);

    @BeforeEach
    void twoTenants() {
        for (String tenant : new String[] {tenantA, tenantA, tenantB}) {
            loader.update("""
                    insert into shipment_deliveries (shipment_id, tenant_id, shipment_number, total_distance_km,
                                                     saved_co2e_kg, delivered_at)
                    values (?, ?, 'SHP-1', 10, 1, ?)""", UUID.randomUUID(), tenant, Timestamp.from(Instant.now()));
        }
    }

    private Connection reader() throws SQLException {
        return DriverManager.getConnection(url, "analytics_reader", readerPassword);
    }

    private long count(Statement statement) throws SQLException {
        try (ResultSet rows = statement.executeQuery("select count(*) from shipment_deliveries where tenant_id in ('"
                + tenantA + "', '" + tenantB + "')")) {
            rows.next();
            return rows.getLong(1);
        }
    }

    @Test
    void seesNothingWithoutSayingWhoseRows() throws SQLException {
        try (Connection connection = reader(); Statement statement = connection.createStatement()) {
            assertThat(count(statement)).isZero();
        }
    }

    @Test
    void seesOnlyTheTenantItNames() throws SQLException {
        try (Connection connection = reader(); Statement statement = connection.createStatement()) {
            statement.execute("set app.tenant_id = '" + tenantA + "'");
            assertThat(count(statement)).isEqualTo(2);
            statement.execute("set app.tenant_id = '" + tenantB + "'");
            assertThat(count(statement)).isEqualTo(1);
        }
    }

    @Test
    void seesEveryTenantOnlyWhenItSaysSo() throws SQLException {
        try (Connection connection = reader(); Statement statement = connection.createStatement()) {
            statement.execute("set app.all_tenants = 'on'");
            assertThat(count(statement)).isEqualTo(3);
        }
    }

    @Test
    void cannotWidenItsScopeInsideAStatement() throws SQLException {
        try (Connection connection = reader(); Statement statement = connection.createStatement()) {
            statement.execute("set app.tenant_id = '" + tenantA + "'");
            assertThatThrownBy(() -> statement.executeQuery(
                    "select set_config('app.all_tenants', 'on', false), count(*) from shipment_deliveries"))
                    .hasMessageContaining("permission denied for function set_config");
        }
    }

    @Test
    void readsAndNothingElse() throws SQLException {
        try (Connection connection = reader(); Statement statement = connection.createStatement()) {
            statement.execute("set app.tenant_id = '" + tenantA + "'");
            assertThatThrownBy(() -> statement.executeUpdate("""
                    insert into shipment_deliveries (shipment_id, tenant_id, shipment_number, total_distance_km,
                                                     saved_co2e_kg, delivered_at)
                    values ('%s', '%s', 'SHP-2', 1, 1, now())""".formatted(UUID.randomUUID(), tenantA)))
                    .hasMessageContaining("permission denied for table shipment_deliveries");
            assertThatThrownBy(() -> statement.executeQuery("select * from flyway_schema_history"))
                    .hasMessageContaining("permission denied for table flyway_schema_history");
        }
    }
}
