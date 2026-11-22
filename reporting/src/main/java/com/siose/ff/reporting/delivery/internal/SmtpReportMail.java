package com.siose.ff.reporting.delivery.internal;

import com.siose.ff.reporting.delivery.DeliveryFailedException;
import com.siose.ff.reporting.generation.ReportReady;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/** The email channel. The PDF stays in object storage; the message names the key. */
@Component
@Profile("!migrate")
class SmtpReportMail {

    private final JavaMailSender mail;
    private final String from;

    SmtpReportMail(JavaMailSender mail, @Value("${delivery.mail-from}") String from) {
        this.mail = mail;
        this.from = from;
    }

    void send(String to, ReportReady report) {
        try {
            MimeMessage message = mail.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject("Monthly report " + report.month());
            helper.setText("""
                    Tenant: %s
                    Month: %s
                    Shipments: %d
                    Distance km: %s
                    Saved CO2e kg: %s
                    Object: %s
                    """.formatted(report.tenantId(), report.month(), report.deliveredShipments(),
                    decimal(report.totalDistanceKm()), decimal(report.savedCo2eKg()), report.objectKey()));
            mail.send(message);
        } catch (MessagingException | RuntimeException e) {
            throw new DeliveryFailedException("Mail to " + to + " was refused", e);
        }
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
