package com.studychain.api.order;

import com.studychain.api.listing.Listing;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** "Order" is a reserved word in SQL, so the class is Order but the table is "orders". */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @Column(name = "buyer_wallet", nullable = false)
    private String buyerWallet;

    @Column(name = "seller_wallet", nullable = false)
    private String sellerWallet;

    @Column(name = "amount_usdc", nullable = false, precision = 10, scale = 2)
    private BigDecimal amountUsdc;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Required by JPA. */
    protected Order() {
    }

    public Order(Listing listing, User buyer, User seller) {
        this.listing = listing;
        this.buyer = buyer;
        this.seller = seller;
        this.buyerWallet = buyer.getWalletAddress();
        this.sellerWallet = seller.getWalletAddress();
        this.amountUsdc = listing.getPriceUsdc(); // price is frozen at order time
        this.status = OrderStatus.AWAITING_PAYMENT;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Listing getListing() { return listing; }
    public User getBuyer() { return buyer; }
    public User getSeller() { return seller; }
    public String getBuyerWallet() { return buyerWallet; }
    public String getSellerWallet() { return sellerWallet; }
    public BigDecimal getAmountUsdc() { return amountUsdc; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
