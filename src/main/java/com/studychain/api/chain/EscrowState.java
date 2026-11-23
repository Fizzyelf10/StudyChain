package com.studychain.api.chain;

import com.studychain.api.common.ApiException;
import org.springframework.http.HttpStatus;

/** Mirrors the State enum in StudyEscrow.sol. The numbers must match the contract exactly. */
public enum EscrowState {
    NONE, FUNDED, RELEASED, REFUNDED, DISPUTED;

    public static EscrowState fromIndex(int index) {
        EscrowState[] all = values();
        if (index < 0 || index >= all.length) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Unknown escrow state " + index + " from the contract");
        }
        return all[index];
    }
}
