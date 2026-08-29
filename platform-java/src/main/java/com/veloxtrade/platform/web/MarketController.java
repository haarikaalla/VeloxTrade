package com.veloxtrade.platform.web;

import com.veloxtrade.platform.config.PlatformProperties;
import com.veloxtrade.platform.domain.MarketTick;
import com.veloxtrade.platform.dto.DepthView;
import com.veloxtrade.platform.dto.QuoteView;
import com.veloxtrade.platform.dto.SignalView;
import com.veloxtrade.platform.service.AnalyticsClient;
import com.veloxtrade.platform.service.MarketDataService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public market data: quote, depth ladder, tick history and the analytics signal. */
@RestController
@RequestMapping("/api/market")
public class MarketController {

    private final MarketDataService marketDataService;
    private final AnalyticsClient analyticsClient;
    private final PlatformProperties properties;

    public MarketController(MarketDataService marketDataService,
                            AnalyticsClient analyticsClient,
                            PlatformProperties properties) {
        this.marketDataService = marketDataService;
        this.analyticsClient = analyticsClient;
        this.properties = properties;
    }

    @GetMapping("/quote")
    public QuoteView quote() {
        return marketDataService.currentQuote();
    }

    @GetMapping("/depth")
    public DepthView depth() {
        return marketDataService.currentDepth();
    }

    @GetMapping("/history")
    public List<TickView> history(@RequestParam(defaultValue = "60") int limit) {
        List<MarketTick> ticks = marketDataService.history(properties.symbol(), limit);
        return ticks.reversed().stream()
                .map(tick -> new TickView(tick.getPrice().doubleValue(),
                        tick.getObservedAt().toEpochMilli()))
                .toList();
    }

    @GetMapping("/signal")
    public SignalView signal() {
        QuoteView quote = marketDataService.currentQuote();
        return analyticsClient.predict(quote.symbol(), quote.price(),
                marketDataService.recentReturns());
    }

    public record TickView(double price, long timestamp) {
    }
}
