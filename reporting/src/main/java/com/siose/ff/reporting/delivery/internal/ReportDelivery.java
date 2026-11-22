package com.siose.ff.reporting.delivery.internal;

import com.siose.ff.base.tenant.TenantIds;
import com.siose.ff.reporting.delivery.InvalidReportException;
import com.siose.ff.reporting.delivery.api.ChannelPreferences;
import com.siose.ff.reporting.generation.ReportReady;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Sends one report on each channel the tenant has turned on. A channel already sent for this
 * report is skipped, so a retry after a later channel failed does not send the earlier one again.
 */
@Component
@Profile("!migrate")
public class ReportDelivery {

    private static final Logger log = LoggerFactory.getLogger(ReportDelivery.class);
    private static final String EMAIL = "email";
    private static final String WEBHOOK = "webhook";

    private final ChannelStore store;
    private final SmtpReportMail mail;
    private final HttpReportWebhook webhook;
    private final JsonMapper json;

    ReportDelivery(ChannelStore store, SmtpReportMail mail, HttpReportWebhook webhook, JsonMapper json) {
        this.store = store;
        this.mail = mail;
        this.webhook = webhook;
        this.json = json;
    }

    public void deliver(String body) {
        ReportReady report = read(body);
        ChannelPreferences channels = store.preference(report.tenantId());
        if (channels.emailEnabled()) {
            sendOnce(report, EMAIL, () -> mail.send(channels.emailAddress(), report));
        }
        if (channels.webhookEnabled()) {
            sendOnce(report, WEBHOOK, () -> webhook.post(channels.webhookUrl(), report));
        }
    }

    private void sendOnce(ReportReady report, String channel, Runnable send) {
        if (store.alreadySent(report.reportId(), channel)) {
            log.debug("Report {} already sent on {}", report.reportId(), channel);
            return;
        }
        send.run();
        store.markSent(report.reportId(), report.tenantId(), channel, Instant.now());
        log.info("Report {} sent on {} for {}", report.reportId(), channel, report.tenantId());
    }

    private ReportReady read(String body) {
        if (body == null || body.isBlank()) {
            throw new InvalidReportException("Empty message");
        }
        ReportReady report;
        try {
            report = json.readValue(body, ReportReady.class);
        } catch (JacksonException e) {
            throw new InvalidReportException("Message is not a report-ready event");
        }
        if (report.reportId() == null || report.month() == null || report.objectKey() == null) {
            throw new InvalidReportException("Message misses reportId, month or objectKey");
        }
        try {
            TenantIds.require(report.tenantId());
        } catch (RuntimeException e) {
            throw new InvalidReportException("Message has no tenant");
        }
        return report;
    }
}
