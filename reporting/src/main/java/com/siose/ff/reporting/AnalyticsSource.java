package com.siose.ff.reporting;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The analytics database, read as {@code analytics_reader}. A second login on purpose: the
 * reporting tables are on this process's own database, and row-level security applies only to
 * this one.
 */
@ConfigurationProperties("analytics.datasource")
public record AnalyticsSource(String url, String username, String password) {
}
