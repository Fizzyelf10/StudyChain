package com.studychain.api.common;

import java.time.Instant;
import java.util.Map;

/** The JSON shape of every error. fieldErrors is only filled for validation failures. */
public record ErrorResponse(Instant timestamp, int status, String error, String message,
                            Map<String, String> fieldErrors) {

    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(Instant.now(), status, error, message, Map.of());
    }
}
