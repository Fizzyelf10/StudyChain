package com.studychain.api.order;

/** Lifecycle of an order. The money state lives on-chain; the backend mirrors it with "sync". */
public enum OrderStatus {
    /** Order created, buyer has not locked the payment yet. */
    AWAITING_PAYMENT,
    /** Payment is locked in escrow. The buyer may download the file. */
    FUNDED,
    /** Buyer confirmed; the seller was paid. */
    COMPLETED,
    /** Buyer raised a dispute; funds stay locked until the arbiter decides. */
    DISPUTED,
    /** The buyer got the money back. */
    REFUNDED
}
