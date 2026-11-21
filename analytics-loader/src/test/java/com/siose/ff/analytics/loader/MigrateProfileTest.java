package com.siose.ff.analytics.loader;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.WebApplicationContext;

/** The hook Job's profile: no web server and no consumers, so it migrates, finishes and exits. */
@SpringBootTest(properties = "spring.flyway.enabled=false")
@ActiveProfiles("migrate")
class MigrateProfileTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private KafkaListenerEndpointRegistry listeners;

    @Test
    void startsWithoutAWebServerAndWithoutConsumers() {
        assertThat(context).isNotInstanceOf(WebApplicationContext.class);
        assertThat(listeners.getListenerContainers()).allSatisfy(container -> assertThat(container.isRunning()).isFalse());
    }
}
