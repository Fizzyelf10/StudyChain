package com.studychain.api.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, UUID> {

    boolean existsByListingIdAndReporterIdAndStatus(UUID listingId, UUID reporterId, ReportStatus status);

    long countByListingIdAndStatus(UUID listingId, ReportStatus status);

    List<Report> findByListingIdAndStatus(UUID listingId, ReportStatus status);

    List<Report> findByStatusOrderByCreatedAtDesc(ReportStatus status);
}
