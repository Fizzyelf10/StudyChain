package com.studychain.api.report;

import com.studychain.api.order.OrderDtos.OrderResponse;
import com.studychain.api.order.OrderService;
import com.studychain.api.order.OrderStatus;
import com.studychain.api.report.ReportDtos.ReportResponse;
import com.studychain.api.report.ReportDtos.ResolveReportRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Everything under /api/admin requires the ADMIN role (enforced in SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final ReportService reports;
    private final OrderService orders;

    public AdminController(ReportService reports, OrderService orders) {
        this.reports = reports;
        this.orders = orders;
    }

    @GetMapping("/reports")
    public List<ReportResponse> reports(@RequestParam(defaultValue = "OPEN") ReportStatus status) {
        return reports.list(status);
    }

    @PostMapping("/reports/{id}/resolve")
    public ReportResponse resolve(@PathVariable UUID id, @Valid @RequestBody ResolveReportRequest request) {
        return reports.resolve(id, request);
    }

    /** Disputed orders waiting for the arbiter. The arbiter settles them on-chain with resolve(). */
    @GetMapping("/orders")
    public List<OrderResponse> orders(@RequestParam(defaultValue = "DISPUTED") OrderStatus status) {
        return orders.byStatus(status);
    }
}
