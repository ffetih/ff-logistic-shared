package com.siose.ff.reporting.generation.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

/** The page prints the figures it was given, and nothing is fetched to draw it. */
class MonthlyReportPdfTest {

    @Test
    void printsTheMonthItWasGiven() throws Exception {
        byte[] pdf = new MonthlyReportPdf().render("ff-eu-freight", YearMonth.of(2026, 11),
                new MonthFigures(40, 1200.5, 80.25));

        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("ff-eu-freight", "2026-11", "40", "1200.500", "80.250");
        }
    }
}
