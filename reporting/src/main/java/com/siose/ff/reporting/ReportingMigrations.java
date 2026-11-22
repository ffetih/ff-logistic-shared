package com.siose.ff.reporting;

import com.siose.ff.base.persistence.MigrationArea;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The process's own tables. The outbox is a separate area, declared by ff-base-events. */
@Configuration
class ReportingMigrations {

    @Bean
    MigrationArea generationMigrations() {
        return MigrationArea.of("generation", "generation");
    }
}
