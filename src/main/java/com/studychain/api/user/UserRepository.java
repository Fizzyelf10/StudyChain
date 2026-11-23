package com.studychain.api.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Spring generates the implementation. Method names are turned into SQL queries. */
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByWalletAddressAndIdNot(String walletAddress, UUID id);
}
