package com.studychain.api.user;

import com.studychain.api.common.ApiException;
import com.studychain.api.config.AppProperties;
import com.studychain.api.security.JwtService;
import com.studychain.api.user.AuthDtos.AuthResponse;
import com.studychain.api.user.AuthDtos.LoginRequest;
import com.studychain.api.user.AuthDtos.RegisterRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Registration and login. Business rules live here, not in the controller. */
@Service
@Transactional
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AppProperties props;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                       AppProperties props) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.props = props;
    }

    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("An account with this email already exists");
        }
        Role role = email.equalsIgnoreCase(props.adminEmail()) ? Role.ADMIN : Role.USER;
        User user = users.save(new User(email, passwordEncoder.encode(request.password()),
                request.displayName().trim(), role));
        return new AuthResponse(jwtService.createToken(user), UserResponse.from(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmail(request.email().trim().toLowerCase()).orElse(null);
        // Same message for "no such user" and "wrong password" so attackers cannot probe for emails
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized("Wrong email or password");
        }
        return new AuthResponse(jwtService.createToken(user), UserResponse.from(user));
    }

    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        return users.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> ApiException.unauthorized("Account no longer exists"));
    }
}
