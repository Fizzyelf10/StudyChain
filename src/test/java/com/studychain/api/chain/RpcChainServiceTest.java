package com.studychain.api.chain;

import com.studychain.api.common.ApiException;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests the hand-written decoding of the contract's getEscrow() answer, with no network. */
class RpcChainServiceTest {

    private static String word(String hex) {
        return "0".repeat(64 - hex.length()) + hex;
    }

    @Test
    void functionSelectorMatchesTheSolidityContract() {
        // keccak256("getEscrow(bytes32)") first 4 bytes, verified against the compiled contract with ethers.js
        assertEquals("0xf023b811", RpcChainService.SELECTOR);
    }

    @Test
    void decodesAFundedEscrow() {
        String buyer = "ab".repeat(20);
        String seller = "cd".repeat(20);
        String result = "0x" + word(buyer) + word(seller) + word("1e8480") + word("1"); // 0x1e8480 = 2,000,000

        EscrowSnapshot snapshot = RpcChainService.decode(result).orElseThrow();

        assertEquals("0x" + buyer, snapshot.buyer());
        assertEquals("0x" + seller, snapshot.seller());
        assertEquals(BigInteger.valueOf(2_000_000), snapshot.amount());
        assertEquals(EscrowState.FUNDED, snapshot.state());
    }

    @Test
    void stateNumbersMatchTheContractEnum() {
        assertEquals(EscrowState.RELEASED, EscrowState.fromIndex(2));
        assertEquals(EscrowState.REFUNDED, EscrowState.fromIndex(3));
        assertEquals(EscrowState.DISPUTED, EscrowState.fromIndex(4));
    }

    @Test
    void anEscrowThatDoesNotExistDecodesToEmpty() {
        String result = "0x" + word("0") + word("0") + word("0") + word("0");
        Optional<EscrowSnapshot> snapshot = RpcChainService.decode(result);
        assertTrue(snapshot.isEmpty());
    }

    @Test
    void tooShortAnswersAreRejected() {
        assertThrows(ApiException.class, () -> RpcChainService.decode("0x"));
    }
}
