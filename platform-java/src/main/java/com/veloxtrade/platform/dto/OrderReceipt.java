package com.veloxtrade.platform.dto;

import com.veloxtrade.platform.domain.TradeOrder;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderReceipt(
        UUID orderId,
        String symbol,
        String side,
        String status,
        long quantity,
        long filledQuantity,
        BigDecimal limitPrice,
        BigDecimal averageFillPrice,
        Long matchLatencyNanos,
        Instant createdAt) {

    public static OrderReceipt from(TradeOrder order) {
        return new OrderReceipt(order.getId(), order.getSymbol(), order.getSide().name(),
                order.getStatus().name(), order.getQuantity(), order.getFilledQuantity(),
                order.getLimitPrice(), order.getAverageFillPrice(), order.getMatchLatencyNanos(),
                order.getCreatedAt());
    }
}
