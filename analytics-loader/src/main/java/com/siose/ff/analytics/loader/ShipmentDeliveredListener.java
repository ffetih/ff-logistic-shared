package com.siose.ff.analytics.loader;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Loads every delivered shipment into the analytics database.
 *
 * <p>The offset is committed after this method returns, so a crash between the write and the
 * commit reads the message again; {@link DeliveryStore#store} makes that harmless. What happens on
 * a failure is {@link KafkaErrorHandling}'s: a broken message goes to the dead-letter topic, a
 * database that is down is waited for.
 */
@Component
public class ShipmentDeliveredListener {

    private static final Logger log = LoggerFactory.getLogger(ShipmentDeliveredListener.class);

    private final DeliveryStore store;
    private final JsonMapper json;
    private final Counter inserted;
    private final Counter duplicates;

    public ShipmentDeliveredListener(DeliveryStore store, JsonMapper json, MeterRegistry meters) {
        this.store = store;
        this.json = json;
        this.inserted = Counter.builder("analytics.loader.deliveries").tag("result", "inserted")
                .description("Delivered shipments read from Kafka").register(meters);
        this.duplicates = Counter.builder("analytics.loader.deliveries").tag("result", "duplicate")
                .description("Delivered shipments read from Kafka").register(meters);
    }

    @KafkaListener(topics = ShipmentDeliveredMessage.TOPIC)
    public void on(ConsumerRecord<String, String> record) {
        ShipmentDeliveredMessage delivery = ShipmentDeliveredMessage.parse(record.value(), json);
        if (store.store(delivery)) {
            inserted.increment();
            log.debug("Loaded delivery {} of tenant {}", delivery.shipmentId(), delivery.tenantId());
        } else {
            duplicates.increment();
            log.debug("Delivery {} was already loaded", delivery.shipmentId());
        }
    }
}
