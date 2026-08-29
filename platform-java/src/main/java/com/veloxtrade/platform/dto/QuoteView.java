package com.veloxtrade.platform.dto;

import java.math.BigDecimal;

/** Latest traded price plus best bid/ask for a symbol. */
public record QuoteView(
        String symbol,
        BigDecimal price,
        BigDecimal bid,
        BigDecimal ask,
        long restingOrders,
        long timestamp) {
}
