package com.veloxtrade.platform.dto;

import java.time.Instant;
import java.util.List;

/** RFC-style error body returned for every handled failure. */
public record ApiError(Instant timestamp, int status, String error, List<String> details) {

    public static ApiError of(int status, String error, List<String> details) {
        return new ApiError(Instant.now(), status, error, details);
    }
}
