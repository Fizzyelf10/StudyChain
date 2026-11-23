package com.studychain.api.order;

import com.studychain.api.order.OrderDtos.CreateOrderRequest;
import com.studychain.api.order.OrderDtos.OrderResponse;
import com.studychain.api.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping
    public OrderResponse create(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody CreateOrderRequest request) {
        return service.create(user, request.listingId());
    }

    @GetMapping("/purchases")
    public List<OrderResponse> purchases(@AuthenticationPrincipal AuthUser user) {
        return service.purchases(user);
    }

    @GetMapping("/sales")
    public List<OrderResponse> sales(@AuthenticationPrincipal AuthUser user) {
        return service.sales(user);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable UUID id, @AuthenticationPrincipal AuthUser user) {
        return service.get(id, user);
    }

    /** Call after any wallet action (fund, release, dispute...) to refresh the order from the escrow. */
    @PostMapping("/{id}/sync")
    public OrderResponse sync(@PathVariable UUID id, @AuthenticationPrincipal AuthUser user) {
        return service.sync(id, user);
    }
}
