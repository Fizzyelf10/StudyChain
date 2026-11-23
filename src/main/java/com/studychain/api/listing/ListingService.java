package com.studychain.api.listing;

import com.studychain.api.common.ApiException;
import com.studychain.api.common.PageResponse;
import com.studychain.api.listing.ListingDtos.CreateListingRequest;
import com.studychain.api.listing.ListingDtos.ListingResponse;
import com.studychain.api.order.OrderRepository;
import com.studychain.api.order.OrderStatus;
import com.studychain.api.security.AuthUser;
import com.studychain.api.storage.FileStorageService;
import com.studychain.api.storage.StoredFile;
import com.studychain.api.user.User;
import com.studychain.api.user.UserRepository;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Business rules for listings. Controllers stay thin and call this class. */
@Service
@Transactional
public class ListingService {

    /** Order states in which the buyer has paid and may download the file. */
    private static final List<OrderStatus> PAID = List.of(OrderStatus.FUNDED, OrderStatus.COMPLETED,
            OrderStatus.DISPUTED);

    private final ListingRepository listings;
    private final UserRepository users;
    private final OrderRepository orders;
    private final FileStorageService storage;

    public ListingService(ListingRepository listings, UserRepository users, OrderRepository orders,
                          FileStorageService storage) {
        this.listings = listings;
        this.users = users;
        this.orders = orders;
        this.storage = storage;
    }

    public record DownloadFile(Resource resource, String filename) {
    }

    @Transactional(readOnly = true)
    public PageResponse<ListingResponse> search(String q, ListingKind kind, int page, int size) {
        Pageable pageable = pageOf(page, size);
        String pattern = "%" + (q == null ? "" : q.trim().toLowerCase()) + "%";
        Page<Listing> result = (kind == null)
                ? listings.search(ListingStatus.ACTIVE, pattern, pageable)
                : listings.searchByKind(ListingStatus.ACTIVE, kind, pattern, pageable);
        return PageResponse.from(result, ListingResponse::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<ListingResponse> mine(AuthUser user, int page, int size) {
        return PageResponse.from(listings.findBySellerIdOrderByCreatedAtDesc(user.id(), pageOf(page, size)),
                ListingResponse::from);
    }

    /** Active listings are public. Hidden ones are visible only to the seller and admins. */
    @Transactional(readOnly = true)
    public ListingResponse get(UUID id, AuthUser viewer) {
        Listing listing = find(id);
        if (listing.getStatus() != ListingStatus.ACTIVE && !isSellerOrAdmin(listing, viewer)) {
            throw ApiException.notFound("Listing not found");
        }
        return ListingResponse.from(listing);
    }

    public ListingResponse create(AuthUser user, CreateListingRequest request, MultipartFile file) {
        User seller = users.findById(user.id())
                .orElseThrow(() -> ApiException.unauthorized("Account no longer exists"));
        if (seller.getWalletAddress() == null) {
            throw ApiException.badRequest("Link a wallet first, so buyers can pay you");
        }

        StoredFile stored = storage.store(file);
        // The same bytes cannot be listed twice. This also stops a removed file from coming back.
        if (listings.existsByFileSha256(stored.sha256())) {
            storage.delete(stored.key());
            throw ApiException.conflict("This exact file has already been listed");
        }

        Listing listing = new Listing(seller, request.title().trim(), blankToNull(request.description()),
                request.courseCode().trim().toUpperCase(), request.university().trim(), request.kind(),
                request.source(), request.sourceNote().trim(), request.priceUsdc(), stored);
        return ListingResponse.from(listings.save(listing));
    }

    public void remove(UUID id, AuthUser user) {
        Listing listing = find(id);
        if (!isSellerOrAdmin(listing, user)) {
            throw ApiException.forbidden("Only the seller or an admin can remove a listing");
        }
        listing.setStatus(ListingStatus.REMOVED);
    }

    @Transactional(readOnly = true)
    public Optional<Resource> preview(UUID id) {
        Listing listing = find(id);
        if (listing.getStatus() != ListingStatus.ACTIVE || !listing.isHasPreview()) {
            return Optional.empty();
        }
        return storage.preview(listing.getFileKey());
    }

    /** The paywall: only the seller, admins, and buyers whose payment is locked in escrow get the file. */
    @Transactional(readOnly = true)
    public DownloadFile download(UUID id, AuthUser user) {
        Listing listing = find(id);
        boolean allowed;
        if (isSellerOrAdmin(listing, user)) {
            allowed = true;
        } else if (listing.getStatus() == ListingStatus.REMOVED) {
            allowed = false; // taken down: nobody else gets the file
        } else {
            allowed = orders.existsByListingIdAndBuyerIdAndStatusIn(id, user.id(), PAID);
        }
        if (!allowed) {
            throw ApiException.forbidden("Buy this file to download it");
        }
        return new DownloadFile(storage.pdf(listing.getFileKey()), listing.getOriginalFilename());
    }

    private Listing find(UUID id) {
        return listings.findById(id).orElseThrow(() -> ApiException.notFound("Listing not found"));
    }

    private static boolean isSellerOrAdmin(Listing listing, AuthUser user) {
        return user != null && (user.isAdmin() || listing.getSeller().getId().equals(user.id()));
    }

    private static Pageable pageOf(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
