package com.siose.ff.reporting.delivery.api;

/**
 * Where a tenant's monthly report is sent. A channel that is off carries no address.
 */
public record ChannelPreferences(
        boolean emailEnabled,
        String emailAddress,
        boolean webhookEnabled,
        String webhookUrl) {

    public static ChannelPreferences none() {
        return new ChannelPreferences(false, null, false, null);
    }
}
