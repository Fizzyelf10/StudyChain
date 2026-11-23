package com.studychain.api.order;

import com.studychain.api.chain.ChainService;
import com.studychain.api.chain.EscrowIds;
import com.studychain.api.chain.EscrowSnapshot;
import com.studychain.api.common.ApiException;
import com.studychain.api.config.AppProperties;
import com.studychain.api.listing.Listing;
import com.studychain.api.listing.ListingRepository;
import com.studychain.api.listing.ListingStatus;
import com.studychain.api.order.OrderDtos.EscrowInfo;
import com.studychain.api.order.OrderDtos.OrderResponse;
import com.studychain.api.security.AuthUser;
import com.studychain.api.user.User;
import com.studychain.api.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Order lifecycle. The backend never moves money. The buyer funds and releases the escrow from
 * their own wallet; "sync" then reads the escrow state and updates the order to match.
 */
@Service
@Transactional
public class OrderService {

    private static final List<OrderStatus> OPEN =
            List.of(OrderStatus.AWAITING_PAYMENT, OrderStatus.FUNDED, OrderStatus.DISPUTED);
    private static final List<OrderStatus> PAID =
            List.of(OrderStatus.FUNDED, OrderStatus.COMPLETED, OrderStatus.DISPUTED);

    private final OrderRepository orders;
    private final ListingRepository listings;
    private final UserRepository users;
    private final ChainService chain;
    private final AppProperties props;

    public OrderService(OrderRepository orders, ListingRepository listings, UserRepository users,
                        ChainService chain, AppProperties props) {
        this.orders = orders;
        this.listings = listings;
        this.users = users;
        this.chain = chain;
        this.props = props;
    }

    public OrderResponse create(AuthUser principal, UUID listingId) {
        Listing listing = listings.findById(listingId)
                .filter(l -> l.getStatus() == ListingStatus.ACTIVE)
                .orElseThrow(() -> ApiException.notFound("Listing not available"));
        User buyer = users.findById(principal.id())
                .orElseThrow(() -> ApiException.unauthorized("Account no longer exists"));
        User seller = listing.getSeller();

        if (seller.getId().equals(buyer.getId())) {
            throw ApiException.badRequest("You cannot buy your own listing");
        }
        if (buyer.getWalletAddress() == null) {
            throw ApiException.badRequest("Link a wallet before buying");
        }
        if (seller.getWalletAddress() == null) {
            throw ApiException.badRequest("The seller has no wallet linked");
        }
        if (buyer.getWalletAddress().equalsIgnoreCase(seller.getWalletAddress())) {
            throw ApiException.badRequest("Buyer and seller cannot use the same wallet");
        }
        if (orders.existsByListingIdAndBuyerIdAndStatusIn(listingId, buyer.getId(), List.of(OrderStatus.COMPLETED))) {
            throw ApiException.conflict("You already bought this file");
        }

        // Clicking "Buy" twice must not create two orders: reuse the open one
        Optional<Order> open = orders.findFirstByListingIdAndBuyerIdAndStatusIn(listingId, buyer.getId(), OPEN);
        Order order = open.orElseGet(() -> orders.save(new Order(listing, buyer, seller)));
        return toResponse(order);
    }

    /** Reads the escrow and brings the order up to date. Safe to call as often as you like. */
    public OrderResponse sync(UUID orderId, AuthUser user) {
        Order order = findVisible(orderId, user);
        Optional<EscrowSnapshot> snapshot = chain.getEscrow(EscrowIds.fromOrderId(order.getId()));
        if (snapshot.isPresent()) {
            EscrowSnapshot escrow = snapshot.get();
            // Never trust an escrow that does not match the order's wallets and price
            boolean matches = escrow.buyer().equalsIgnoreCase(order.getBuyerWallet())
                    && escrow.seller().equalsIgnoreCase(order.getSellerWallet())
                    && escrow.amount().equals(EscrowIds.toUnits(order.getAmountUsdc()));
            if (!matches) {
                throw ApiException.conflict("The on-chain escrow does not match this order");
            }
            order.setStatus(switch (escrow.state()) {
                case FUNDED -> OrderStatus.FUNDED;
                case RELEASED -> OrderStatus.COMPLETED;
                case REFUNDED -> OrderStatus.REFUNDED;
                case DISPUTED -> OrderStatus.DISPUTED;
                case NONE -> order.getStatus();
            });
        }
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(UUID orderId, AuthUser user) {
        return toResponse(findVisible(orderId, user));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> purchases(AuthUser user) {
        return orders.findByBuyerIdOrderByCreatedAtDesc(user.id()).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> sales(AuthUser user) {
        return orders.findBySellerIdOrderByCreatedAtDesc(user.id()).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> byStatus(OrderStatus status) {
        return orders.findByStatusOrderByCreatedAtDesc(status).stream().map(this::toResponse).toList();
    }

    /** An order is visible to its buyer, its seller and admins. Others get 404, not 403, to leak nothing. */
    private Order findVisible(UUID orderId, AuthUser user) {
        Order order = orders.findById(orderId).orElseThrow(() -> ApiException.notFound("Order not found"));
        boolean visible = user.isAdmin()
                || order.getBuyer().getId().equals(user.id())
                || order.getSeller().getId().equals(user.id());
        if (!visible) {
            throw ApiException.notFound("Order not found");
        }
        return order;
    }

    private OrderResponse toResponse(Order o) {
        AppProperties.Chain cfg = props.chain();
        EscrowInfo escrow = new EscrowInfo(EscrowIds.fromOrderId(o.getId()), cfg.escrowContract(),
                cfg.tokenContract(), cfg.chainId(), EscrowIds.toUnits(o.getAmountUsdc()).toString());
        return new OrderResponse(o.getId(), o.getListing().getId(), o.getListing().getTitle(), o.getStatus(),
                o.getAmountUsdc(), o.getBuyer().getDisplayName(), o.getSeller().getDisplayName(),
                o.getBuyerWallet(), o.getSellerWallet(), escrow, PAID.contains(o.getStatus()), o.getCreatedAt());
    }
}
