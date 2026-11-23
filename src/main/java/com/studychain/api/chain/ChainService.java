package com.studychain.api.chain;

import java.util.Optional;

/**
 * How the backend learns what happened to an escrow. The backend only READS the escrow. Moving
 * money (fund, release, ...) is always done by the users from their own wallets.
 * Two implementations: MockChainService (mode "mock") and RpcChainService (mode "rpc").
 */
public interface ChainService {

    /** Empty when no escrow exists yet for this id. */
    Optional<EscrowSnapshot> getEscrow(String escrowId);
}
