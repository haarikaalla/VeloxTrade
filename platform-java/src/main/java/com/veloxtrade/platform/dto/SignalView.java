package com.veloxtrade.platform.dto;

/** Directional signal returned by the Python analytics service. */
public record SignalView(
        String symbol,
        String direction,
        double confidence,
        double momentum,
        double volatility,
        int horizonSeconds,
        String disclaimer) {
}
