package com.studychain.api.chain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscrowIdsTest {

    @Test
    void orderIdBecomesA32ByteHexValue() {
        UUID id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        String escrowId = EscrowIds.fromOrderId(id);

        assertEquals(66, escrowId.length()); // "0x" + 64 hex characters = 32 bytes
        assertTrue(escrowId.startsWith("0x" + "0".repeat(32)));
        assertTrue(escrowId.endsWith("123e4567e89b12d3a456426614174000"));
    }

    @Test
    void convertsUsdcToSixDecimalUnits() {
        assertEquals(BigInteger.valueOf(2_000_000), EscrowIds.toUnits(new BigDecimal("2.00")));
        assertEquals(BigInteger.valueOf(500_000), EscrowIds.toUnits(new BigDecimal("0.50")));
        assertEquals(BigInteger.valueOf(123_450_000), EscrowIds.toUnits(new BigDecimal("123.45")));
    }
}
