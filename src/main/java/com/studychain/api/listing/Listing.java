package com.studychain.api.listing;

import com.studychain.api.storage.StoredFile;
import com.studychain.api.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One row of the "listings" table: a file for sale. */
@Entity
@Table(name = "listings")
public class Listing {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(name = "course_code", nullable = false)
    private String courseCode;

    @Column(nullable = false)
    private String university;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ListingKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ListingSource source;

    @Column(name = "source_note", nullable = false)
    private String sourceNote;

    /** BigDecimal, never double: money needs exact decimals. */
    @Column(name = "price_usdc", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceUsdc;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ListingStatus status;

    @Column(name = "file_key", nullable = false)
    private String fileKey;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(name = "file_sha256", nullable = false, unique = true)
    private String fileSha256;

    @Column(name = "page_count", nullable = false)
    private int pageCount;

    @Column(name = "has_preview", nullable = false)
    private boolean hasPreview;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Required by JPA. */
    protected Listing() {
    }

    public Listing(User seller, String title, String description, String courseCode, String university,
                   ListingKind kind, ListingSource source, String sourceNote, BigDecimal priceUsdc,
                   StoredFile file) {
        this.seller = seller;
        this.title = title;
        this.description = description;
        this.courseCode = courseCode;
        this.university = university;
        this.kind = kind;
        this.source = source;
        this.sourceNote = sourceNote;
        this.priceUsdc = priceUsdc;
        this.status = ListingStatus.ACTIVE;
        this.fileKey = file.key();
        this.originalFilename = file.originalName();
        this.fileSizeBytes = file.sizeBytes();
        this.fileSha256 = file.sha256();
        this.pageCount = file.pageCount();
        this.hasPreview = file.hasPreview();
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public User getSeller() { return seller; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getCourseCode() { return courseCode; }
    public String getUniversity() { return university; }
    public ListingKind getKind() { return kind; }
    public ListingSource getSource() { return source; }
    public String getSourceNote() { return sourceNote; }
    public BigDecimal getPriceUsdc() { return priceUsdc; }
    public ListingStatus getStatus() { return status; }
    public void setStatus(ListingStatus status) { this.status = status; }
    public String getFileKey() { return fileKey; }
    public String getOriginalFilename() { return originalFilename; }
    public long getFileSizeBytes() { return fileSizeBytes; }
    public String getFileSha256() { return fileSha256; }
    public int getPageCount() { return pageCount; }
    public boolean isHasPreview() { return hasPreview; }
    public Instant getCreatedAt() { return createdAt; }
}
