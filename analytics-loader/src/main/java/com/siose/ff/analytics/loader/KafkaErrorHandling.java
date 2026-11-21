package com.siose.ff.analytics.loader;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * What happens when a message cannot be loaded. Two kinds of failure, two answers:
 * <ul>
 *   <li>A broken message ({@link InvalidMessageException}) will never load. It goes to the
 *       loader's dead-letter topic ({@code <topic>.analytics.dlt}) at once, and the next message is
 *       read.</li>
 *   <li>Anything else, typically the database being down, is retried with a growing pause and no
 *       end. The partition waits instead of skipping: no delivery is lost, none is loaded twice
 *       out of turn.</li>
 * </ul>
 * Spring Boot gives this handler to every {@code @KafkaListener}.
 *
 * <p>Unlike the services, which try twice and then give up to their dead-letter topic, the loader
 * waits for its database without end: a delivery it set aside would be missing from every figure
 * computed afterwards, and nobody would notice. The dead-letter topic is its own: another consumer
 * of the same topic has one of its own.
 */
@Configuration
@Profile("!migrate")
public class KafkaErrorHandling {

    static final String DEAD_LETTER_SUFFIX = ".analytics.dlt";

    private static final Logger log = LoggerFactory.getLogger(KafkaErrorHandling.class);

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> template, MeterRegistry meters,
            @Value("${analytics.loader.retry-first-delay:PT1S}") Duration firstDelay,
            @Value("${analytics.loader.retry-max-delay:PT30S}") Duration maxDelay) {
        Counter deadLettered = Counter.builder("analytics.loader.dead.letters")
                .description("Messages sent to a dead-letter topic because they can never be loaded")
                .register(meters);
        // Partition -1: the producer picks it from the key, so a shipment's messages stay together.
        DeadLetterPublishingRecoverer deadLetters = new DeadLetterPublishingRecoverer(template,
                (record, exception) -> new TopicPartition(record.topic() + DEAD_LETTER_SUFFIX, -1));
        ConsumerRecordRecoverer recoverer = (record, exception) -> {
            log.warn("Message {}-{}@{} sent to {}{}: {}", record.topic(), record.partition(), record.offset(),
                    record.topic(), DEAD_LETTER_SUFFIX, reason(exception));
            deadLetters.accept(record, exception);
            deadLettered.increment();
        };

        ExponentialBackOff backOff = new ExponentialBackOff(firstDelay.toMillis(), 2.0);
        backOff.setMaxInterval(maxDelay.toMillis());
        backOff.setMaxElapsedTime(Long.MAX_VALUE);
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(InvalidMessageException.class);
        return handler;
    }

    /** Created when the loader starts, if missing: the broker creates no topic by itself. */
    @Bean
    NewTopic shipmentDeliveredDeadLetters(@Value("${ff.kafka.partitions:3}") int partitions,
            @Value("${ff.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(ShipmentDeliveredMessage.TOPIC + DEAD_LETTER_SUFFIX)
                .partitions(partitions).replicas(replicas).build();
    }

    /** The message of the {@link InvalidMessageException} in the chain, otherwise of the deepest cause. */
    private static String reason(Throwable exception) {
        Throwable cause = exception;
        while (!(cause instanceof InvalidMessageException) && cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }
}
