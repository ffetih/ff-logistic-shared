package com.siose.ff.reporting.generation.internal;

import com.siose.ff.base.storage.ObjectStoragePort;
import com.siose.ff.base.storage.StorageObjectDescriptor;
import com.siose.ff.base.tenant.TenantContext;
import com.siose.ff.base.web.ResourceNotFoundException;
import com.siose.ff.reporting.generation.ReportReady;
import com.siose.ff.reporting.generation.api.ReportView;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Builds a month once, then returns the stored row. The event goes out only for the new row. */
@Service
public class MonthlyReportService {

    static final Duration LINK_VALID_FOR = Duration.ofMinutes(15);
    private static final String CATEGORY = "report";
    private static final String FILE_NAME = "monthly.pdf";

    private final ReportStore reports;
    private final MonthlyFiguresReader figures;
    private final MonthlyReportPdf pdf;
    private final ObjectStoragePort storage;
    private final ApplicationEventPublisher events;

    MonthlyReportService(ReportStore reports, MonthlyFiguresReader figures, MonthlyReportPdf pdf,
            ObjectStoragePort storage, ApplicationEventPublisher events) {
        this.reports = reports;
        this.figures = figures;
        this.pdf = pdf;
        this.storage = storage;
        this.events = events;
    }

    public record Generated(ReportView report, boolean created) {
    }

    @Transactional
    public Generated generate(String monthText) {
        YearMonth month = month(monthText);
        String tenantId = TenantContext.requireTenantId();
        var existing = reports.find(tenantId, month);
        if (existing.isPresent()) {
            return new Generated(view(existing.get()), false);
        }
        MonthFigures monthFigures = figures.read(tenantId, month.atDay(1));
        byte[] document = pdf.render(tenantId, month, monthFigures);
        StorageObjectDescriptor stored = storage.store(
                tenantId, CATEGORY, month.toString(), FILE_NAME, "application/pdf", document);
        Instant generatedAt = Instant.now();
        StoredReport report = ReportStore.newReport(UUID.randomUUID(), tenantId, month.atDay(1),
                stored.objectKey(), monthFigures, generatedAt);
        if (reports.insert(report).isEmpty()) {
            return new Generated(view(reports.find(tenantId, month).orElseThrow()), false);
        }
        events.publishEvent(new ReportReady(report.id(), tenantId, month.toString(), stored.objectKey(),
                monthFigures.deliveredShipments(), monthFigures.totalDistanceKm(), monthFigures.savedCo2eKg(),
                generatedAt));
        return new Generated(view(report), true);
    }

    @Transactional(readOnly = true)
    public List<ReportView> list() {
        return reports.list(TenantContext.requireTenantId()).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public ReportView get(UUID id) {
        return reports.find(TenantContext.requireTenantId(), id).map(this::view)
                .orElseThrow(() -> new ResourceNotFoundException("No report " + id));
    }

    private ReportView view(StoredReport report) {
        StorageObjectDescriptor document = storage.registerDocumentReference(
                report.tenantId(), CATEGORY, YearMonth.from(report.deliveryMonth()).toString(), FILE_NAME,
                "application/pdf");
        return new ReportView(report.id(), YearMonth.from(report.deliveryMonth()).toString(),
                report.deliveredShipments(), report.totalDistanceKm(), report.savedCo2eKg(), report.generatedAt(),
                storage.downloadUrl(document, LINK_VALID_FOR).toString());
    }

    private static YearMonth month(String text) {
        try {
            return YearMonth.parse(text);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "month is a calendar month, yyyy-MM");
        }
    }
}
