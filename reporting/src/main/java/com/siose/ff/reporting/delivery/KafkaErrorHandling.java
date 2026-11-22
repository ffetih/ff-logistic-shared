package com.siose.ff.reporting.delivery;

import com.siose.ff.reporting.generation.ReportReady;
import java.time.Duration;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * A failed send is tried again {@code delivery.retry-attempts} times, waiting
 * {@code delivery.retry-delay} between tries, then the record goes to this consumer's dead-letter
 * topic, {@code ff.logistics.reporting.report-ready.reporting-delivery.dlt}. A message that is not
 * a report goes there at once. The pause and the number of tries come from the environment, so the
 * cluster can use a short pause and a dead letter shows up within a few seconds.
 */
@Configuration(proxyBeanMethods = false)
@Profile("!migrate")
class KafkaErrorHandling {

    static final String DEAD_LETTER_SUFFIX = ".reporting-delivery.dlt";

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaOperations<Object, Object> kafka,
            @Value("${delivery.retry-delay:PT1S}") Duration delay,
            @Value("${delivery.retry-attempts:2}") long attempts) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafka,
                (record, exception) -> new TopicPartition(record.topic() + DEAD_LETTER_SUFFIX, -1));
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(delay.toMillis(), attempts));
        handler.addNotRetryableExceptions(InvalidReportException.class);
        return handler;
    }

    /** Created when the process starts, if missing: the broker creates no topic by itself. */
    @Bean
    NewTopic reportReadyDeadLetters(@Value("${ff.kafka.partitions:3}") int partitions,
            @Value("${ff.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(ReportReady.TOPIC + DEAD_LETTER_SUFFIX).partitions(partitions).replicas(replicas).build();
    }
}
