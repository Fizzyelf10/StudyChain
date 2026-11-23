package com.studychain.api.wallet;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WalletServiceTest {

    @Test
    void acceptsWellFormedAddresses() {
        assertTrue(WalletService.isValidAddress("0x" + "a".repeat(40)));
        assertTrue(WalletService.isValidAddress("0x52908400098527886E0F7030069857D2E4169EE7"));
    }

    @Test
    void rejectsMalformedAddresses() {
        assertFalse(WalletService.isValidAddress(null));
        assertFalse(WalletService.isValidAddress(""));
        assertFalse(WalletService.isValidAddress("0x123"));
        assertFalse(WalletService.isValidAddress("a".repeat(42)));
        assertFalse(WalletService.isValidAddress("0x" + "g".repeat(40)));
    }

    @Test
    void messageContainsAddressAccountAndNonce() {
        UUID user = UUID.randomUUID();
        String message = WalletService.buildMessage("0xABCDEFabcdefABCDEFabcdefABCDEFabcdefABCD", user, "nonce42");
        assertTrue(message.contains("0xabcdefabcdefabcdefabcdefabcdefabcdefabcd"));
        assertTrue(message.contains(user.toString()));
        assertTrue(message.contains("nonce42"));
    }
}
