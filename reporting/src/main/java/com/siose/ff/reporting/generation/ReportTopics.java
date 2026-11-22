package com.siose.ff.reporting.generation;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;

/**
 * The topic generation writes to. Spring's KafkaAdmin creates it when this process starts; the
 * broker creates none by itself. Delivery's dead-letter topic is that module's own.
 *
 * <p>Not in the migration Job, which should not wait for a broker, and only when events are sent.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.modulith.events.externalization.enabled", havingValue = "true")
@Profile("!migrate")
class ReportTopics {

    @Bean
    NewTopic reportReadyTopic(@Value("${ff.kafka.partitions:3}") int partitions,
            @Value("${ff.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(ReportReady.TOPIC).partitions(partitions).replicas(replicas).build();
    }
}
