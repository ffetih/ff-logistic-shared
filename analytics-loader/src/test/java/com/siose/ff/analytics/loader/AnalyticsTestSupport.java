package com.siose.ff.analytics.loader;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * A real PostgreSQL and an in-process Kafka broker for every test class that extends this one; the
 * Spring context is the same for all of them, so it starts once.
 *
 * <p>The database is PostgreSQL 17 in a container (CI), or an existing server when
 * {@code ANALYTICS_TEST_DB_URL} names one (with {@code ANALYTICS_TEST_DB_USERNAME} and
 * {@code _PASSWORD}): the loader's SQL is PostgreSQL's, so an in-memory database would not do.
 * Without either, the classes that use this are skipped ({@link #databaseAvailable}).
 */
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(topics = ShipmentDeliveredMessage.TOPIC)
abstract class AnalyticsTestSupport {

    static final String CONDITION = "com.siose.ff.analytics.loader.AnalyticsTestSupport#databaseAvailable";

    private static final String EXISTING = System.getenv("ANALYTICS_TEST_DB_URL");

    private static PostgreSQLContainer postgres;

    static boolean databaseAvailable() {
        return EXISTING != null || DockerClientFactory.instance().isDockerAvailable();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        if (EXISTING != null) {
            registry.add("spring.datasource.url", () -> EXISTING);
            registry.add("spring.datasource.username", () -> System.getenv("ANALYTICS_TEST_DB_USERNAME"));
            registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("ANALYTICS_TEST_DB_PASSWORD", ""));
            return;
        }
        synchronized (AnalyticsTestSupport.class) {
            if (postgres == null) {
                postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
                postgres.start();
            }
        }
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
