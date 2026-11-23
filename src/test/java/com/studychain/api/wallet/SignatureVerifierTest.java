package com.studychain.api.wallet;

import com.studychain.api.common.ApiException;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Plain unit test: no Spring, no database. Signs a message like a wallet would, then verifies it. */
class SignatureVerifierTest {

    private final SignatureVerifier verifier = new SignatureVerifier();

    private static String sign(String message, ECKeyPair keyPair) {
        Sign.SignatureData data = Sign.signPrefixedMessage(message.getBytes(StandardCharsets.UTF_8), keyPair);
        byte[] sig = new byte[65];
        System.arraycopy(data.getR(), 0, sig, 0, 32);
        System.arraycopy(data.getS(), 0, sig, 32, 32);
        sig[64] = data.getV()[0];
        return Numeric.toHexString(sig);
    }

    @Test
    void recoversTheAddressThatSigned() throws Exception {
        ECKeyPair keyPair = Keys.createEcKeyPair();
        String expected = "0x" + Keys.getAddress(keyPair);
        String message = WalletService.buildMessage(expected, java.util.UUID.randomUUID(), "abc123");

        assertEquals(expected.toLowerCase(), verifier.recoverAddress(message, sign(message, keyPair)));
    }

    @Test
    void aDifferentMessageRecoversADifferentAddress() throws Exception {
        ECKeyPair keyPair = Keys.createEcKeyPair();
        String expected = "0x" + Keys.getAddress(keyPair);
        String signature = sign("message one", keyPair);

        assertNotEquals(expected.toLowerCase(), verifier.recoverAddress("message two", signature));
    }

    @Test
    void rejectsSignaturesOfTheWrongLength() {
        assertThrows(ApiException.class, () -> verifier.recoverAddress("hello", "0x1234"));
    }

    @Test
    void rejectsNonHexSignatures() {
        assertThrows(ApiException.class, () -> verifier.recoverAddress("hello", "not hex at all"));
    }
}
