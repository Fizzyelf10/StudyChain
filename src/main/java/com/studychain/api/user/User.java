package com.studychain.api.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One row of the "users" table. Never sent to the client directly, see UserResponse. */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /** Lower-case 0x address, set only after the user proved ownership by signing a message. */
    @Column(name = "wallet_address")
    private String walletAddress;

    @Column(name = "wallet_nonce")
    private String walletNonce;

    @Column(name = "wallet_nonce_expires_at")
    private Instant walletNonceExpiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Required by JPA. */
    protected User() {
    }

    public User(String email, String passwordHash, String displayName, Role role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.role = role;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public Role getRole() { return role; }
    public String getWalletAddress() { return walletAddress; }
    public void setWalletAddress(String walletAddress) { this.walletAddress = walletAddress; }
    public String getWalletNonce() { return walletNonce; }
    public void setWalletNonce(String walletNonce) { this.walletNonce = walletNonce; }
    public Instant getWalletNonceExpiresAt() { return walletNonceExpiresAt; }
    public void setWalletNonceExpiresAt(Instant at) { this.walletNonceExpiresAt = at; }
    public Instant getCreatedAt() { return createdAt; }
}
