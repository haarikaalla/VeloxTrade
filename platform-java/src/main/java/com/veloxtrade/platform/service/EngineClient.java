package com.veloxtrade.platform.service;

import com.veloxtrade.platform.domain.OrderSide;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Thin HTTP client for the C++ matching engine. */
@Service
public class EngineClient {

    private final RestClient restClient;

    public EngineClient(@Qualifier("engineRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public EngineQuote quote() {
        return call(() -> restClient.get().uri("/quote").retrieve().body(EngineQuote.class),
                "matching engine quote");
    }

    public EngineDepth depth() {
        return call(() -> restClient.get().uri("/depth").retrieve().body(EngineDepth.class),
                "matching engine depth");
    }

    public EngineOrderResult submit(OrderSide side, long quantity, BigDecimal limitPrice) {
        Map<String, Object> payload = Map.of(
                "side", side.name(),
                "quantity", quantity,
                "limitPrice", limitPrice.toPlainString());
        return call(() -> restClient.post()
                        .uri("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(payload)
                        .retrieve()
                        .body(EngineOrderResult.class),
                "matching engine order submission");
    }

    private <T> T call(java.util.function.Supplier<T> operation, String description) {
        try {
            T result = operation.get();
            if (result == null) {
                throw new UpstreamUnavailableException(description + " returned an empty body", null);
            }
            return result;
        } catch (RestClientException ex) {
            throw new UpstreamUnavailableException(description + " is unavailable", ex);
        }
    }

    public record EngineQuote(String symbol, BigDecimal price, BigDecimal bid, BigDecimal ask,
                              long restingOrders, long timestamp) {
    }

    public record EngineLevel(BigDecimal price, long quantity) {
    }

    public record EngineDepth(String symbol, List<EngineLevel> bids, List<EngineLevel> asks,
                              long timestamp) {
    }

    public record EngineFill(BigDecimal price, long quantity) {
    }

    public record EngineOrderResult(long orderId, String status, long filledQuantity,
                                    long restingQuantity, long matchLatencyNanos,
                                    List<EngineFill> fills, long executedAt) {
    }
}
