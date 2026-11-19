package com.siose.ff.logistics.warehousing;

import com.siose.ff.base.persistence.MigrationArea;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The warehousing area as the application sees it. Found by component scanning in either
 * implementation (both applications sit in {@code com.siose.ff.logistics}).
 *
 * <p>Its tables are migrated from {@code db/migration/warehousing} with their own history table,
 * {@code warehousing_flyway_history} (decision 3), whichever process runs the area.
 */
@Configuration(proxyBeanMethods = false)
public class WarehousingArea {

    public static final String NAME = "warehousing";

    @Bean
    MigrationArea warehousingMigrations() {
        return MigrationArea.of(NAME);
    }
}
