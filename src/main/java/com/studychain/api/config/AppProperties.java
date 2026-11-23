package com.studychain.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed view of the "studychain.*" section of application.yml. */
@ConfigurationProperties(prefix = "studychain")
public record AppProperties(String adminEmail, Jwt jwt, Storage storage, Chain chain) {

    public record Jwt(String secret, long expirationMinutes) {
    }

    public record Storage(String directory) {
    }

    /** mode is "mock" or "rpc". */
    public record Chain(String mode, String rpcUrl, long chainId, String escrowContract, String tokenContract) {
    }
}
