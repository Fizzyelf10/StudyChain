package com.studychain.api.listing;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ListingRepository extends JpaRepository<Listing, UUID> {

    /** Search by title, course code or university. pattern is already lower-case and wrapped in %...%. */
    @Query("""
            select l from Listing l
            where l.status = :status
              and (lower(l.title) like :pattern
                   or lower(l.courseCode) like :pattern
                   or lower(l.university) like :pattern)
            order by l.createdAt desc
            """)
    Page<Listing> search(@Param("status") ListingStatus status, @Param("pattern") String pattern,
                         Pageable pageable);

    @Query("""
            select l from Listing l
            where l.status = :status
              and l.kind = :kind
              and (lower(l.title) like :pattern
                   or lower(l.courseCode) like :pattern
                   or lower(l.university) like :pattern)
            order by l.createdAt desc
            """)
    Page<Listing> searchByKind(@Param("status") ListingStatus status, @Param("kind") ListingKind kind,
                               @Param("pattern") String pattern, Pageable pageable);

    Page<Listing> findBySellerIdOrderByCreatedAtDesc(UUID sellerId, Pageable pageable);

    boolean existsByFileSha256(String fileSha256);
}
