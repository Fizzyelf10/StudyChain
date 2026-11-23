package com.studychain.api.order;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record CreateOrderRequest(@NotNull UUID listingId) {
    }

    /** Everything a wallet needs to fund the escrow. amountUnits is a string because it can exceed JS numbers. */
    public record EscrowInfo(String escrowId, String escrowContract, String tokenContract, long chainId,
                             String amountUnits) {
    }

    public record OrderResponse(UUID id, UUID listingId, String listingTitle, OrderStatus status,
                                BigDecimal amountUsdc, String buyerName, String sellerName, String buyerWallet,
                                String sellerWallet, EscrowInfo escrow, boolean canDownload, Instant createdAt) {
    }
}
