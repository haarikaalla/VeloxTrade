package com.veloxtrade.platform.dto;

import java.math.BigDecimal;
import java.util.List;

/** Mark-to-market snapshot of an account. */
public record PortfolioView(
        String displayName,
        BigDecimal cashBalance,
        BigDecimal positionsValue,
        BigDecimal netLiquidation,
        BigDecimal unrealizedPnl,
        List<PositionView> positions) {

    public record PositionView(
            String symbol,
            long quantity,
            BigDecimal averagePrice,
            BigDecimal lastPrice,
            BigDecimal marketValue,
            BigDecimal unrealizedPnl) {
    }
}
