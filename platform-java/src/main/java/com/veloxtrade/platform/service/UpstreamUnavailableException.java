package com.veloxtrade.platform.service;

/** Raised when the matching engine or analytics service cannot be reached. */
public class UpstreamUnavailableException extends RuntimeException {

    public UpstreamUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
