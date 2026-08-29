package com.veloxtrade.platform.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PositionTest {

    private static final UUID ACCOUNT = UUID.randomUUID();

    @Test
    void buysBuildWeightedAverageCost() {
        Position position = new Position(ACCOUNT, "VLX");

        position.apply(OrderSide.BUY, 10, new BigDecimal("100.00"));
        position.apply(OrderSide.BUY, 30, new BigDecimal("110.00"));

        assertThat(position.getQuantity()).isEqualTo(40);
        assertThat(position.getAveragePrice()).isEqualByComparingTo("107.5000");
    }

    @Test
    void sellsReduceQuantityAndKeepCostBasis() {
        Position position = new Position(ACCOUNT, "VLX");
        position.apply(OrderSide.BUY, 50, new BigDecimal("20.00"));

        position.apply(OrderSide.SELL, 20, new BigDecimal("25.00"));

        assertThat(position.getQuantity()).isEqualTo(30);
        assertThat(position.getAveragePrice()).isEqualByComparingTo("20.0000");
    }

    @Test
    void fullyClosingResetsAveragePrice() {
        Position position = new Position(ACCOUNT, "VLX");
        position.apply(OrderSide.BUY, 15, new BigDecimal("42.00"));

        position.apply(OrderSide.SELL, 15, new BigDecimal("44.00"));

        assertThat(position.getQuantity()).isZero();
        assertThat(position.getAveragePrice()).isEqualByComparingTo("0");
    }
}
