package com.siose.ff.reporting.generation.internal;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.YearMonth;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** The monthly page. HTML in this process, PDFBox underneath. The figures are printed as given. */
@Component
class MonthlyReportPdf {

    byte[] render(String tenantId, YearMonth month, MonthFigures figures) {
        String html = """
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head>
                  <style>
                    body { font-family: sans-serif; font-size: 12pt; }
                    h1 { font-size: 16pt; }
                    td { padding: 4pt 12pt 4pt 0; }
                  </style>
                </head>
                <body>
                  <h1>Monthly deliveries</h1>
                  <table>
                    <tr><td>Tenant</td><td>%s</td></tr>
                    <tr><td>Month</td><td>%s</td></tr>
                    <tr><td>Delivered shipments</td><td>%d</td></tr>
                    <tr><td>Distance km</td><td>%s</td></tr>
                    <tr><td>CO2e saved kg</td><td>%s</td></tr>
                  </table>
                </body>
                </html>
                """.formatted(text(tenantId), text(month.toString()), figures.deliveredShipments(),
                decimal(figures.totalDistanceKm()), decimal(figures.savedCo2eKg()));
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String text(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
