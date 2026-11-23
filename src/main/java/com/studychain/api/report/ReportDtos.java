package com.studychain.api.report;

import com.studychain.api.listing.ListingStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class ReportDtos {

    private ReportDtos() {
    }

    public enum ResolveAction {
        /** The report was wrong. The listing goes back on sale if no other reports are open. */
        DISMISS,
        /** The report was right. The listing is taken down for good. */
        REMOVE_LISTING
    }

    public record CreateReportRequest(@NotNull ReportReason reason, @Size(max = 1000) String details) {
    }

    public record ResolveReportRequest(@NotNull ResolveAction action, @Size(max = 500) String note) {
    }

    public record ReportResponse(UUID id, UUID listingId, String listingTitle, ListingStatus listingStatus,
                                 String sellerName, String reporterName, ReportReason reason, String details,
                                 ReportStatus status, String resolutionNote, Instant createdAt) {

        public static ReportResponse from(Report r) {
            return new ReportResponse(r.getId(), r.getListing().getId(), r.getListing().getTitle(),
                    r.getListing().getStatus(), r.getListing().getSeller().getDisplayName(),
                    r.getReporter().getDisplayName(), r.getReason(), r.getDetails(), r.getStatus(),
                    r.getResolutionNote(), r.getCreatedAt());
        }
    }
}
