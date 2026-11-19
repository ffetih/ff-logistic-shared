package com.siose.ff.logistics.transportation;

import com.siose.ff.base.persistence.MigrationArea;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The transportation area as the application sees it. Found by component scanning in either
 * implementation (both applications sit in {@code com.siose.ff.logistics}).
 *
 * <p>Its tables are migrated from {@code db/migration/transportation} with their own history table,
 * {@code transportation_flyway_history} (decision 3), whichever process runs the area.
 */
@Configuration(proxyBeanMethods = false)
public class TransportationArea {

    public static final String NAME = "transportation";

    @Bean
    MigrationArea transportationMigrations() {
        return MigrationArea.of(NAME);
    }
}
