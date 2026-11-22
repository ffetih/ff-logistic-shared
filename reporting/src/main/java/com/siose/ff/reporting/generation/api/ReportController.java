package com.siose.ff.reporting.generation.api;

import com.siose.ff.reporting.generation.internal.MonthlyReportService;
import com.siose.ff.reporting.generation.internal.MonthlyReportService.Generated;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasAnyRole('LOGISTICS_PLANNER', 'REPORT_VIEWER', 'TENANT_ADMIN')")
public class ReportController {

    private final MonthlyReportService reports;

    public ReportController(MonthlyReportService reports) {
        this.reports = reports;
    }

    @PostMapping("/monthly")
    public ResponseEntity<ReportView> monthly(@Valid @RequestBody MonthRequest request) {
        Generated generated = reports.generate(request.month());
        HttpStatus status = generated.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(generated.report());
    }

    @GetMapping
    public List<ReportView> list() {
        return reports.list();
    }

    @GetMapping("/{id}")
    public ReportView get(@PathVariable UUID id) {
        return reports.get(id);
    }
}
