package com.veloxtrade.platform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Immutable audit record of an order routed to the matching engine. */
@Entity
@Table(name = "orders")
public class TradeOrder {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(nullable = false, length = 12)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private OrderSide side;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(nullable = false)
    private long quantity;

    @Column(name = "filled_quantity", nullable = false)
    private long filledQuantity;

    @Column(name = "limit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal limitPrice;

    @Column(name = "average_fill_price", precision = 19, scale = 4)
    private BigDecimal averageFillPrice;

    @Column(name = "engine_order_id")
    private Long engineOrderId;

    @Column(name = "match_latency_nanos")
    private Long matchLatencyNanos;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TradeOrder() {
        // required by JPA
    }

    public TradeOrder(UUID accountId, String symbol, OrderSide side, long quantity,
                      BigDecimal limitPrice, OrderStatus status, long filledQuantity,
                      BigDecimal averageFillPrice, Long engineOrderId, Long matchLatencyNanos) {
        this.id = UUID.randomUUID();
        this.accountId = accountId;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.limitPrice = limitPrice;
        this.status = status;
        this.filledQuantity = filledQuantity;
        this.averageFillPrice = averageFillPrice;
        this.engineOrderId = engineOrderId;
        this.matchLatencyNanos = matchLatencyNanos;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getSymbol() {
        return symbol;
    }

    public OrderSide getSide() {
        return side;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public long getQuantity() {
        return quantity;
    }

    public long getFilledQuantity() {
        return filledQuantity;
    }

    public BigDecimal getLimitPrice() {
        return limitPrice;
    }

    public BigDecimal getAverageFillPrice() {
        return averageFillPrice;
    }

    public Long getEngineOrderId() {
        return engineOrderId;
    }

    public Long getMatchLatencyNanos() {
        return matchLatencyNanos;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
