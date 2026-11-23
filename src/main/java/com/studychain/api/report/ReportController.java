package com.studychain.api.report;

import com.studychain.api.report.ReportDtos.CreateReportRequest;
import com.studychain.api.report.ReportDtos.ReportResponse;
import com.studychain.api.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @PostMapping("/api/listings/{id}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse report(@PathVariable UUID id, @AuthenticationPrincipal AuthUser user,
                                 @Valid @RequestBody CreateReportRequest request) {
        return service.create(id, user, request);
    }
}
