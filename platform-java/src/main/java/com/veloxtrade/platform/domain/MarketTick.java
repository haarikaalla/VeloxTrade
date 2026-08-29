package com.veloxtrade.platform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Time-series row of the simulated market, stored in the TimescaleDB hypertable. */
@Entity
@Table(name = "market_ticks")
public class MarketTick {

    @Id
    private UUID id;

    @Column(nullable = false, length = 12)
    private String symbol;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Column(precision = 19, scale = 4)
    private BigDecimal bid;

    @Column(precision = 19, scale = 4)
    private BigDecimal ask;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    protected MarketTick() {
        // required by JPA
    }

    public MarketTick(String symbol, BigDecimal price, BigDecimal bid, BigDecimal ask,
                      Instant observedAt) {
        this.id = UUID.randomUUID();
        this.symbol = symbol;
        this.price = price;
        this.bid = bid;
        this.ask = ask;
        this.observedAt = observedAt;
    }

    public String getSymbol() {
        return symbol;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getBid() {
        return bid;
    }

    public BigDecimal getAsk() {
        return ask;
    }

    public Instant getObservedAt() {
        return observedAt;
    }
}
