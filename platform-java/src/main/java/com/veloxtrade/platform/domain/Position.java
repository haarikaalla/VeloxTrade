package com.veloxtrade.platform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/** Net holding of a symbol for one account, carrying a weighted average cost. */
@Entity
@Table(name = "positions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"account_id", "symbol"}))
public class Position {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(nullable = false, length = 12)
    private String symbol;

    @Column(nullable = false)
    private long quantity;

    @Column(name = "average_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal averagePrice;

    @Version
    @Column(nullable = false)
    private long version;

    protected Position() {
        // required by JPA
    }

    public Position(UUID accountId, String symbol) {
        this.id = UUID.randomUUID();
        this.accountId = accountId;
        this.symbol = symbol;
        this.quantity = 0L;
        this.averagePrice = BigDecimal.ZERO;
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

    public long getQuantity() {
        return quantity;
    }

    public BigDecimal getAveragePrice() {
        return averagePrice;
    }

    /** Applies a fill, recomputing weighted average cost when the position grows. */
    public void apply(OrderSide side, long filledQuantity, BigDecimal fillPrice) {
        if (side == OrderSide.BUY) {
            BigDecimal existingCost = averagePrice.multiply(BigDecimal.valueOf(quantity));
            BigDecimal addedCost = fillPrice.multiply(BigDecimal.valueOf(filledQuantity));
            long newQuantity = quantity + filledQuantity;
            this.averagePrice = newQuantity == 0
                    ? BigDecimal.ZERO
                    : existingCost.add(addedCost)
                            .divide(BigDecimal.valueOf(newQuantity), 4, RoundingMode.HALF_UP);
            this.quantity = newQuantity;
        } else {
            this.quantity -= filledQuantity;
            if (this.quantity == 0) {
                this.averagePrice = BigDecimal.ZERO;
            }
        }
    }
}
