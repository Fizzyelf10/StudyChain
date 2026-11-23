package com.studychain.api.security;

import com.studychain.api.user.Role;

import java.util.UUID;

/** The logged-in user as seen by controllers. Built from the JWT, so no database lookup per request. */
public record AuthUser(UUID id, String email, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
