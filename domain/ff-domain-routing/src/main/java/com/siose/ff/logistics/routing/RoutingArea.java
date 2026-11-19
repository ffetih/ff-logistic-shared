package com.siose.ff.logistics.routing;

import com.siose.ff.base.persistence.MigrationArea;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The routing area as the application sees it. Found by component scanning in either
 * implementation (both applications sit in {@code com.siose.ff.logistics}).
 *
 * <p>Its tables are migrated from {@code db/migration/routing} with their own history table,
 * {@code routing_flyway_history} (decision 3), whichever process runs the area.
 */
@Configuration(proxyBeanMethods = false)
public class RoutingArea {

    public static final String NAME = "routing";

    @Bean
    MigrationArea routingMigrations() {
        return MigrationArea.of(NAME);
    }
}
