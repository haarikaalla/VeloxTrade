package com.veloxtrade.platform.service;

import com.veloxtrade.platform.dto.SignalView;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Client for the Python analytics service. */
@Service
public class AnalyticsClient {

    private final RestClient restClient;

    public AnalyticsClient(@Qualifier("analyticsRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public SignalView predict(String symbol, BigDecimal lastPrice, List<Double> recentReturns) {
        try {
            SignalView signal = restClient.post()
                    .uri("/predict")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "symbol", symbol,
                            "lastPrice", lastPrice,
                            "recentReturns", recentReturns))
                    .retrieve()
                    .body(SignalView.class);
            if (signal == null) {
                throw new UpstreamUnavailableException("analytics service returned no signal", null);
            }
            return signal;
        } catch (RestClientException ex) {
            throw new UpstreamUnavailableException("analytics service is unavailable", ex);
        }
    }
}
