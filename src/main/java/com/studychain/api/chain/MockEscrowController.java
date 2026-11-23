package com.studychain.api.chain;

import com.studychain.api.security.AuthUser;
import com.studychain.api.user.UserResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Demo endpoints, present only in mock mode. After using them the UI calls the normal "sync". */
@RestController
@RequestMapping("/api/dev")
@ConditionalOnProperty(name = "studychain.chain.mode", havingValue = "mock", matchIfMissing = true)
public class MockEscrowController {

    public record DevWalletRequest(@NotBlank String address) {
    }

    private final MockEscrowService service;

    public MockEscrowController(MockEscrowService service) {
        this.service = service;
    }

    /** action is one of: fund, release, refund, dispute, resolve (resolve also takes ?toSeller=true|false) */
    @PostMapping("/escrow/{orderId}/{action}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void act(@PathVariable UUID orderId, @PathVariable String action,
                    @RequestParam(defaultValue = "true") boolean toSeller,
                    @AuthenticationPrincipal AuthUser caller) {
        service.act(orderId, action, caller, toSeller);
    }

    @PostMapping("/wallet")
    public UserResponse linkWallet(@AuthenticationPrincipal AuthUser caller,
                                   @Valid @RequestBody DevWalletRequest request) {
        return service.linkWalletWithoutSignature(caller.id(), request.address());
    }
}
