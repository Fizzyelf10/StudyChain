package com.studychain.api.chain;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A pretend blockchain kept in memory, so the whole app can be demoed without a wallet or a
 * testnet. It forgets everything on restart. Active when studychain.chain.mode=mock (the default).
 */
@Service
@ConditionalOnProperty(name = "studychain.chain.mode", havingValue = "mock", matchIfMissing = true)
public class MockChainService implements ChainService {

    private final Map<String, EscrowSnapshot> escrows = new ConcurrentHashMap<>();

    @Override
    public Optional<EscrowSnapshot> getEscrow(String escrowId) {
        return Optional.ofNullable(escrows.get(escrowId));
    }

    void put(String escrowId, EscrowSnapshot snapshot) {
        escrows.put(escrowId, snapshot);
    }
}
