package com.studychain.api.wallet;

import com.studychain.api.common.ApiException;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SignatureException;
import java.util.Arrays;

/**
 * Checks an Ethereum "personal_sign" signature. A wallet signs the text with its private key; from
 * the text and the signature we can recover which address signed it, without ever seeing the key.
 */
@Component
public class SignatureVerifier {

    /** Returns the lower-case 0x address that produced the signature over the message. */
    public String recoverAddress(String message, String signatureHex) {
        byte[] sig;
        try {
            sig = Numeric.hexStringToByteArray(signatureHex);
        } catch (RuntimeException e) {
            throw ApiException.badRequest("Signature is not valid hex");
        }
        if (sig.length != 65) {
            throw ApiException.badRequest("Signature must be 65 bytes");
        }
        byte v = sig[64];
        if (v < 27) {
            v += 27; // some wallets send 0/1 instead of 27/28
        }
        Sign.SignatureData data = new Sign.SignatureData(
                v, Arrays.copyOfRange(sig, 0, 32), Arrays.copyOfRange(sig, 32, 64));
        try {
            BigInteger publicKey = Sign.signedPrefixedMessageToKey(message.getBytes(StandardCharsets.UTF_8), data);
            return "0x" + Keys.getAddress(publicKey).toLowerCase();
        } catch (SignatureException | IllegalArgumentException e) {
            throw ApiException.badRequest("Signature could not be verified");
        }
    }
}
