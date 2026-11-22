package com.siose.ff.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.WebApplicationContext;

/** The hook Job's profile: it migrates and exits, with no web server and no mail sender. */
@SpringBootTest
@ActiveProfiles("migrate")
class MigrateProfileTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private KafkaListenerEndpointRegistry listeners;

    @Test
    void migratesWithoutAWebServerOrAConsumer() {
        assertThat(context).isNotInstanceOf(WebApplicationContext.class);
        assertThat(listeners.getListenerContainers()).isEmpty();
        assertThat(context.containsBean("reportDelivery")).isFalse();
    }
}
