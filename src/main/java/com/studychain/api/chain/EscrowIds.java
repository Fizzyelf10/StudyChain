package com.studychain.api.chain;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.UUID;

/** Conversions between our database values and what the escrow contract uses. */
public final class EscrowIds {

    /** USDC has 6 decimals, so 1.50 USDC is 1_500_000 units. */
    public static final int TOKEN_DECIMALS = 6;

    private EscrowIds() {
    }

    /** The contract identifies an escrow by bytes32. We use the order's UUID, left-padded with zeros. */
    public static String fromOrderId(UUID orderId) {
        return "0x" + "0".repeat(32) + orderId.toString().replace("-", "");
    }

    public static BigInteger toUnits(BigDecimal usdc) {
        return usdc.movePointRight(TOKEN_DECIMALS).toBigIntegerExact();
    }
}
