package com.studychain.api.wallet;

import com.studychain.api.security.AuthUser;
import com.studychain.api.user.UserResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    public record ChallengeRequest(@NotBlank String address) {
    }

    public record LinkRequest(@NotBlank String address, @NotBlank String signature) {
    }

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping("/challenge")
    public WalletService.Challenge challenge(@AuthenticationPrincipal AuthUser user,
                                             @Valid @RequestBody ChallengeRequest request) {
        return walletService.challenge(user.id(), request.address());
    }

    @PostMapping("/link")
    public UserResponse link(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody LinkRequest request) {
        return walletService.link(user.id(), request.address(), request.signature());
    }
}
