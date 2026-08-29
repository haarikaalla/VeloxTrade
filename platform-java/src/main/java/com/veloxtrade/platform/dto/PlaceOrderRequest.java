package com.veloxtrade.platform.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public record PlaceOrderRequest(
        @NotNull @Pattern(regexp = "^[A-Z]{1,12}$", message = "symbol must be 1-12 upper case letters")
        String symbol,
        @NotNull @Pattern(regexp = "^(BUY|SELL)$", message = "side must be BUY or SELL") String side,
        @Min(1) @Max(1_000_000) long quantity,
        @NotNull @DecimalMin(value = "0.01") @DecimalMax(value = "1000000.00") BigDecimal limitPrice) {
}
