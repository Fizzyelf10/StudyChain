package com.studychain.api.chain;

import com.studychain.api.common.ApiException;
import com.studychain.api.order.Order;
import com.studychain.api.order.OrderRepository;
import com.studychain.api.security.AuthUser;
import com.studychain.api.user.User;
import com.studychain.api.user.UserRepository;
import com.studychain.api.user.UserResponse;
import com.studychain.api.wallet.WalletService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Development-only stand-in for the wallet. It performs the same state changes that the real
 * contract would (same rules as StudyEscrow.sol), so the demo behaves like production.
 * Does not exist at all when studychain.chain.mode=rpc.
 */
@Service
@Transactional
@ConditionalOnProperty(name = "studychain.chain.mode", havingValue = "mock", matchIfMissing = true)
public class MockEscrowService {

    private final OrderRepository orders;
    private final UserRepository users;
    private final MockChainService chain;

    public MockEscrowService(OrderRepository orders, UserRepository users, MockChainService chain) {
        this.orders = orders;
        this.users = users;
        this.chain = chain;
    }

    public void act(UUID orderId, String action, AuthUser caller, boolean toSeller) {
        Order order = orders.findById(orderId).orElseThrow(() -> ApiException.notFound("Order not found"));
        String id = EscrowIds.fromOrderId(orderId);
        EscrowSnapshot current = chain.getEscrow(id).orElse(null);
        EscrowState state = current == null ? EscrowState.NONE : current.state();
        boolean isBuyer = order.getBuyer().getId().equals(caller.id());
        boolean isSeller = order.getSeller().getId().equals(caller.id());

        switch (action) {
            case "fund" -> {
                require(isBuyer, "Only the buyer can fund");
                require(state == EscrowState.NONE, "Escrow already exists");
                chain.put(id, new EscrowSnapshot(order.getBuyerWallet(), order.getSellerWallet(),
                        EscrowIds.toUnits(order.getAmountUsdc()), EscrowState.FUNDED));
            }
            case "release" -> {
                require(isBuyer, "Only the buyer can release");
                require(state == EscrowState.FUNDED, "Escrow is not funded");
                chain.put(id, current.withState(EscrowState.RELEASED));
            }
            case "refund" -> {
                require(isSeller, "Only the seller can refund");
                require(state == EscrowState.FUNDED || state == EscrowState.DISPUTED, "Nothing to refund");
                chain.put(id, current.withState(EscrowState.REFUNDED));
            }
            case "dispute" -> {
                require(isBuyer, "Only the buyer can dispute");
                require(state == EscrowState.FUNDED, "Escrow is not funded");
                chain.put(id, current.withState(EscrowState.DISPUTED));
            }
            case "resolve" -> {
                require(caller.isAdmin(), "Only the arbiter (admin) can resolve");
                require(state == EscrowState.DISPUTED, "Escrow is not disputed");
                chain.put(id, current.withState(toSeller ? EscrowState.RELEASED : EscrowState.REFUNDED));
            }
            default -> throw ApiException.badRequest("Unknown action: " + action);
        }
    }

    /** Skips the signature step so the demo works without a wallet extension. Never exists in rpc mode. */
    public UserResponse linkWalletWithoutSignature(UUID userId, String address) {
        if (!WalletService.isValidAddress(address)) {
            throw ApiException.badRequest("That is not a valid wallet address");
        }
        String normalized = address.toLowerCase();
        if (users.existsByWalletAddressAndIdNot(normalized, userId)) {
            throw ApiException.conflict("That wallet is already linked to another account");
        }
        User user = users.findById(userId).orElseThrow(() -> ApiException.unauthorized("Account no longer exists"));
        user.setWalletAddress(normalized);
        return UserResponse.from(user);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw ApiException.forbidden(message);
        }
    }
}
