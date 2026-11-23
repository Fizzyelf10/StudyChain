package com.studychain.api.wallet;

import com.studychain.api.common.ApiException;
import com.studychain.api.user.User;
import com.studychain.api.user.UserRepository;
import com.studychain.api.user.UserResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Links a wallet to an account in two steps, so nobody can claim an address they do not control:
 *  1. challenge: the server makes up a one-time text (with a random nonce) for that address
 *  2. link: the user signs the text in their wallet; we recover the signer and compare
 */
@Service
@Transactional
public class WalletService {

    private static final Pattern ADDRESS = Pattern.compile("^0x[0-9a-fA-F]{40}$");

    private final UserRepository users;
    private final SignatureVerifier verifier;

    public WalletService(UserRepository users, SignatureVerifier verifier) {
        this.users = users;
        this.verifier = verifier;
    }

    public record Challenge(String address, String message) {
    }

    public static boolean isValidAddress(String address) {
        return address != null && ADDRESS.matcher(address).matches();
    }

    public static String buildMessage(String address, UUID userId, String nonce) {
        return "StudyChain wants to link wallet " + address.toLowerCase() + " to account " + userId
                + ".\nNonce: " + nonce;
    }

    public Challenge challenge(UUID userId, String address) {
        if (!isValidAddress(address)) {
            throw ApiException.badRequest("That is not a valid wallet address");
        }
        User user = load(userId);
        String nonce = UUID.randomUUID().toString().replace("-", "");
        user.setWalletNonce(nonce);
        user.setWalletNonceExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        return new Challenge(address.toLowerCase(), buildMessage(address, userId, nonce));
    }

    public UserResponse link(UUID userId, String address, String signature) {
        if (!isValidAddress(address)) {
            throw ApiException.badRequest("That is not a valid wallet address");
        }
        User user = load(userId);
        if (user.getWalletNonce() == null || user.getWalletNonceExpiresAt() == null
                || user.getWalletNonceExpiresAt().isBefore(Instant.now())) {
            throw ApiException.badRequest("Challenge expired. Start again.");
        }
        String message = buildMessage(address, userId, user.getWalletNonce());
        String recovered = verifier.recoverAddress(message, signature);
        if (!recovered.equalsIgnoreCase(address)) {
            throw ApiException.forbidden("The signature does not match that wallet address");
        }
        String normalized = address.toLowerCase();
        if (users.existsByWalletAddressAndIdNot(normalized, userId)) {
            throw ApiException.conflict("That wallet is already linked to another account");
        }
        user.setWalletAddress(normalized);
        user.setWalletNonce(null); // a nonce works only once
        user.setWalletNonceExpiresAt(null);
        return UserResponse.from(user);
    }

    private User load(UUID userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.unauthorized("Account no longer exists"));
    }
}
