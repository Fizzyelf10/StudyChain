package com.studychain.api.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    boolean existsByListingIdAndBuyerIdAndStatusIn(UUID listingId, UUID buyerId, Collection<OrderStatus> statuses);

    Optional<Order> findFirstByListingIdAndBuyerIdAndStatusIn(UUID listingId, UUID buyerId,
                                                              Collection<OrderStatus> statuses);

    List<Order> findByBuyerIdOrderByCreatedAtDesc(UUID buyerId);

    List<Order> findBySellerIdOrderByCreatedAtDesc(UUID sellerId);

    List<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status);
}
