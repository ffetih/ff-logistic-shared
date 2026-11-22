package com.siose.ff.reporting.delivery.internal;

import com.siose.ff.reporting.generation.ReportReady;
import java.time.Duration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** The webhook channel. The body is the report-ready event; a non-2xx answer fails the send. */
@Component
@Profile("!migrate")
class HttpReportWebhook {

    private final RestClient http;

    HttpReportWebhook() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    void post(String url, ReportReady report) {
        http.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(report).retrieve().toBodilessEntity();
    }
}
