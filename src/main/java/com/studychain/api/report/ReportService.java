package com.studychain.api.report;

import com.studychain.api.common.ApiException;
import com.studychain.api.listing.Listing;
import com.studychain.api.listing.ListingRepository;
import com.studychain.api.listing.ListingStatus;
import com.studychain.api.report.ReportDtos.CreateReportRequest;
import com.studychain.api.report.ReportDtos.ReportResponse;
import com.studychain.api.report.ReportDtos.ResolveReportRequest;
import com.studychain.api.security.AuthUser;
import com.studychain.api.user.User;
import com.studychain.api.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Community reporting plus admin review. A listing that collects AUTO_HIDE_THRESHOLD open reports is
 * hidden from search right away (UNDER_REVIEW) until an admin decides.
 */
@Service
@Transactional
public class ReportService {

    static final int AUTO_HIDE_THRESHOLD = 3;

    private final ReportRepository reports;
    private final ListingRepository listings;
    private final UserRepository users;

    public ReportService(ReportRepository reports, ListingRepository listings, UserRepository users) {
        this.reports = reports;
        this.listings = listings;
        this.users = users;
    }

    public ReportResponse create(UUID listingId, AuthUser reporterPrincipal, CreateReportRequest request) {
        Listing listing = listings.findById(listingId)
                .filter(l -> l.getStatus() != ListingStatus.REMOVED)
                .orElseThrow(() -> ApiException.notFound("Listing not found"));
        if (listing.getSeller().getId().equals(reporterPrincipal.id())) {
            throw ApiException.badRequest("You cannot report your own listing");
        }
        if (reports.existsByListingIdAndReporterIdAndStatus(listingId, reporterPrincipal.id(), ReportStatus.OPEN)) {
            throw ApiException.conflict("You already reported this listing");
        }
        User reporter = users.findById(reporterPrincipal.id())
                .orElseThrow(() -> ApiException.unauthorized("Account no longer exists"));

        Report report = reports.save(new Report(listing, reporter, request.reason(), blankToNull(request.details())));

        // Each reporter can have only one open report per listing, so this counts distinct people
        long open = reports.countByListingIdAndStatus(listingId, ReportStatus.OPEN);
        if (open >= AUTO_HIDE_THRESHOLD && listing.getStatus() == ListingStatus.ACTIVE) {
            listing.setStatus(ListingStatus.UNDER_REVIEW);
        }
        return ReportResponse.from(report);
    }

    @Transactional(readOnly = true)
    public List<ReportResponse> list(ReportStatus status) {
        return reports.findByStatusOrderByCreatedAtDesc(status).stream().map(ReportResponse::from).toList();
    }

    public ReportResponse resolve(UUID reportId, ResolveReportRequest request) {
        Report report = reports.findById(reportId).orElseThrow(() -> ApiException.notFound("Report not found"));
        if (report.getStatus() != ReportStatus.OPEN) {
            throw ApiException.conflict("This report was already handled");
        }
        Listing listing = report.getListing();
        String note = blankToNull(request.note());

        switch (request.action()) {
            case DISMISS -> {
                report.resolve(ReportStatus.DISMISSED, note);
                // Put the listing back on sale if nobody else is still complaining about it
                boolean othersOpen = reports.countByListingIdAndStatus(listing.getId(), ReportStatus.OPEN) > 0;
                if (!othersOpen && listing.getStatus() == ListingStatus.UNDER_REVIEW) {
                    listing.setStatus(ListingStatus.ACTIVE);
                }
            }
            case REMOVE_LISTING -> {
                listing.setStatus(ListingStatus.REMOVED);
                // Every open report about this listing is settled by the takedown
                for (Report open : reports.findByListingIdAndStatus(listing.getId(), ReportStatus.OPEN)) {
                    open.resolve(ReportStatus.ACTIONED, note);
                }
            }
        }
        return ReportResponse.from(report);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
