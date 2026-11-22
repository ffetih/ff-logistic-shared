package com.siose.ff.reporting.delivery;

import com.siose.ff.reporting.delivery.internal.ReportDelivery;
import com.siose.ff.reporting.generation.ReportReady;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * The delivery group. The offset is committed after this method returns, so a crash reads the
 * message again; a channel already sent is skipped. What happens on a failure is
 * {@link KafkaErrorHandling}'s.
 */
@Component
@Profile("!migrate")
public class ReportReadyListener {

    private final ReportDelivery delivery;

    public ReportReadyListener(ReportDelivery delivery) {
        this.delivery = delivery;
    }

    @KafkaListener(topics = ReportReady.TOPIC, groupId = "reporting-delivery")
    public void on(ConsumerRecord<String, byte[]> record) {
        byte[] body = record.value();
        delivery.deliver(body == null ? null : new String(body, StandardCharsets.UTF_8));
    }
}
