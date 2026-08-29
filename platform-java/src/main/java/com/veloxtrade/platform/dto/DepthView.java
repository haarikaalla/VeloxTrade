package com.veloxtrade.platform.dto;

import java.math.BigDecimal;
import java.util.List;

/** Aggregated order-book depth for the dashboard ladder. */
public record DepthView(String symbol, List<Level> bids, List<Level> asks, long timestamp) {

    public record Level(BigDecimal price, long quantity) {
    }
}
