package com.siose.ff.reporting.delivery.internal;

import com.siose.ff.reporting.delivery.api.ChannelPreferences;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** The delivery schema: one preference per tenant, one sent row per report and channel. */
@Component
class ChannelStore {

    private final JdbcTemplate jdbc;

    ChannelStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    ChannelPreferences preference(String tenantId) {
        return jdbc.query("""
                select email_enabled, email_address, webhook_enabled, webhook_url
                from delivery.channel_preference
                where tenant_id = ?
                """, (rs, n) -> new ChannelPreferences(
                rs.getBoolean("email_enabled"),
                rs.getString("email_address"),
                rs.getBoolean("webhook_enabled"),
                rs.getString("webhook_url")), tenantId)
                .stream().findFirst().orElseGet(ChannelPreferences::none);
    }

    void save(String tenantId, ChannelPreferences preferences) {
        int updated = jdbc.update("""
                update delivery.channel_preference
                set email_enabled = ?, email_address = ?, webhook_enabled = ?, webhook_url = ?
                where tenant_id = ?
                """, preferences.emailEnabled(), preferences.emailAddress(),
                preferences.webhookEnabled(), preferences.webhookUrl(), tenantId);
        if (updated > 0) {
            return;
        }
        try {
            jdbc.update("""
                    insert into delivery.channel_preference
                        (tenant_id, email_enabled, email_address, webhook_enabled, webhook_url)
                    values (?, ?, ?, ?, ?)
                    """, tenantId, preferences.emailEnabled(), preferences.emailAddress(),
                    preferences.webhookEnabled(), preferences.webhookUrl());
        } catch (DuplicateKeyException e) {
            jdbc.update("""
                    update delivery.channel_preference
                    set email_enabled = ?, email_address = ?, webhook_enabled = ?, webhook_url = ?
                    where tenant_id = ?
                    """, preferences.emailEnabled(), preferences.emailAddress(),
                    preferences.webhookEnabled(), preferences.webhookUrl(), tenantId);
        }
    }

    boolean alreadySent(UUID reportId, String channel) {
        Integer count = jdbc.queryForObject("""
                select count(*) from delivery.sent_delivery
                where report_id = ? and channel = ?
                """, Integer.class, reportId, channel);
        return count != null && count > 0;
    }

    void markSent(UUID reportId, String tenantId, String channel, Instant when) {
        try {
            jdbc.update("""
                    insert into delivery.sent_delivery (report_id, channel, tenant_id, delivered_at)
                    values (?, ?, ?, ?)
                    """, reportId, channel, tenantId, Timestamp.from(when));
        } catch (DuplicateKeyException ignored) {
            // A concurrent delivery of the same report and channel already recorded it.
        }
    }
}
