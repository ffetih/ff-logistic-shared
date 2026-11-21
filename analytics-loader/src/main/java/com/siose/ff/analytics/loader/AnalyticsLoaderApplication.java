package com.siose.ff.analytics.loader;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Loads business events from Kafka into the analytics database ({@code ff_logistics_analytics}).
 * It is the database's only writer and owns its schema.
 *
 * <p>Like every image of the product, it has two jobs: normally it consumes; with the
 * {@code migrate} profile (the Argo CD PreSync hook) it starts without web server or consumers,
 * lets Flyway migrate, and exits.
 *
 * <p>It is shared: written once, it runs whichever implementation is active. It knows the topics and
 * the JSON on them, never the code of the side that sends them.
 *
 * <p>{@code @EnableScheduling}: the monthly rollup runs on a schedule ({@link MonthlyDeliveryRollup}).
 */
@SpringBootApplication
@EnableScheduling
public class AnalyticsLoaderApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(AnalyticsLoaderApplication.class, args);
        if (context.getEnvironment().matchesProfiles("migrate")) {
            System.exit(SpringApplication.exit(context));
        }
    }
}
