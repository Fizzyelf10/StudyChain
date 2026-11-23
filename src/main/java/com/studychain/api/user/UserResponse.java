package com.studychain.api.user;

import java.util.UUID;

/** What the API shows about a user. No password hash, no nonce. */
public record UserResponse(UUID id, String email, String displayName, Role role, String walletAddress) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole(),
                user.getWalletAddress());
    }
}
