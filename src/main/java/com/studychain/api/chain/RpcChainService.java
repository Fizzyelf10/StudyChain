package com.studychain.api.chain;

import com.studychain.api.common.ApiException;
import com.studychain.api.config.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads the real StudyEscrow contract through a JSON-RPC node (Infura, Alchemy, a local node...).
 * It calls the contract's read-only function getEscrow(bytes32) with "eth_call" and decodes the
 * answer by hand: four 32-byte words (buyer, seller, amount, state). No private key is needed
 * because the backend never sends transactions.
 */
@Service
@ConditionalOnProperty(name = "studychain.chain.mode", havingValue = "rpc")
public class RpcChainService implements ChainService {

    /** First 4 bytes of keccak256("getEscrow(bytes32)"): how the contract recognises the function. */
    static final String SELECTOR = Hash.sha3String("getEscrow(bytes32)").substring(0, 10);

    private static final int WORD = 64; // one 32-byte word is 64 hex characters

    private final RestClient http;
    private final String escrowContract;

    public RpcChainService(AppProperties props) {
        AppProperties.Chain chain = props.chain();
        if (chain.escrowContract() == null || chain.escrowContract().isBlank()) {
            throw new IllegalStateException("studychain.chain.escrow-contract is required in rpc mode");
        }
        this.escrowContract = chain.escrowContract();
        this.http = RestClient.builder().baseUrl(chain.rpcUrl()).build();
    }

    @Override
    public Optional<EscrowSnapshot> getEscrow(String escrowId) {
        String callData = SELECTOR + Numeric.cleanHexPrefix(escrowId);
        Map<String, Object> request = Map.of(
                "jsonrpc", "2.0",
                "id", 1,
                "method", "eth_call",
                "params", List.of(Map.of("to", escrowContract, "data", callData), "latest"));

        Map<?, ?> response;
        try {
            response = http.post().contentType(MediaType.APPLICATION_JSON).body(request).retrieve().body(Map.class);
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Could not reach the blockchain node");
        }
        if (response == null || !(response.get("result") instanceof String result)) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The blockchain node returned an error: "
                    + (response == null ? "no response" : response.get("error")));
        }
        return decode(result);
    }

    /** Package-private so it can be unit tested without a network. */
    static Optional<EscrowSnapshot> decode(String resultHex) {
        String hex = Numeric.cleanHexPrefix(resultHex);
        if (hex.length() < 4 * WORD) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Unexpected answer from the contract (is the escrow address right?)");
        }
        String buyer = address(hex.substring(0, WORD));
        String seller = address(hex.substring(WORD, 2 * WORD));
        BigInteger amount = new BigInteger(hex.substring(2 * WORD, 3 * WORD), 16);
        EscrowState state = EscrowState.fromIndex(new BigInteger(hex.substring(3 * WORD, 4 * WORD), 16).intValueExact());
        if (state == EscrowState.NONE) {
            return Optional.empty();
        }
        return Optional.of(new EscrowSnapshot(buyer, seller, amount, state));
    }

    /** An address is the last 20 bytes (40 hex characters) of its 32-byte word. */
    private static String address(String word) {
        return "0x" + word.substring(24).toLowerCase();
    }
}
