package com.studychain.api.common;

import com.studychain.api.config.AppProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public settings the web UI needs to know how to take payments. Nothing secret in here. */
@RestController
public class ConfigController {

    public record PublicConfig(String chainMode, long chainId, String escrowContract, String tokenContract,
                               String tokenSymbol, int tokenDecimals) {
    }

    private final AppProperties props;

    public ConfigController(AppProperties props) {
        this.props = props;
    }

    @GetMapping("/api/config")
    public PublicConfig config() {
        AppProperties.Chain chain = props.chain();
        return new PublicConfig(chain.mode(), chain.chainId(), chain.escrowContract(), chain.tokenContract(),
                "USDC", 6);
    }
}
