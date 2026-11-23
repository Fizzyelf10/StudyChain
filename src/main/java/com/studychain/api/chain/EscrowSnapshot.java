package com.studychain.api.chain;

import java.math.BigInteger;

/** The state of one escrow at one moment. Addresses are lower-case 0x strings, amount is in token units. */
public record EscrowSnapshot(String buyer, String seller, BigInteger amount, EscrowState state) {

    public EscrowSnapshot withState(EscrowState newState) {
        return new EscrowSnapshot(buyer, seller, amount, newState);
    }
}
