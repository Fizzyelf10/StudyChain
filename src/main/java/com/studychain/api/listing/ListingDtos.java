package com.studychain.api.listing;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Request and response shapes for listings. The JPA entity is never sent to the client. */
public final class ListingDtos {

    private ListingDtos() {
    }

    public record CreateListingRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 2000) String description,
            @NotBlank @Size(max = 20) String courseCode,
            @NotBlank @Size(max = 100) String university,
            @NotNull ListingKind kind,
            @NotNull ListingSource source,
            @NotBlank @Size(min = 10, max = 500, message = "explain where the file comes from (10 to 500 characters)")
            String sourceNote,
            @NotNull @DecimalMin(value = "0.50", message = "minimum price is 0.50 USDC")
            @DecimalMax(value = "500.00", message = "maximum price is 500 USDC")
            @Digits(integer = 3, fraction = 2, message = "use at most 2 decimals") BigDecimal priceUsdc,
            @AssertTrue(message = "you must confirm you may sell this file") boolean ownershipConfirmed) {
    }

    public record ListingResponse(
            UUID id, String title, String description, String courseCode, String university,
            ListingKind kind, ListingSource source, String sourceNote, BigDecimal priceUsdc,
            int pageCount, boolean hasPreview, UUID sellerId, String sellerName, ListingStatus status,
            Instant createdAt) {

        public static ListingResponse from(Listing l) {
            return new ListingResponse(l.getId(), l.getTitle(), l.getDescription(), l.getCourseCode(),
                    l.getUniversity(), l.getKind(), l.getSource(), l.getSourceNote(), l.getPriceUsdc(),
                    l.getPageCount(), l.isHasPreview(), l.getSeller().getId(), l.getSeller().getDisplayName(),
                    l.getStatus(), l.getCreatedAt());
        }
    }
}
