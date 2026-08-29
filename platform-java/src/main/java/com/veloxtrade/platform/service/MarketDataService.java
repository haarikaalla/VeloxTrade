package com.veloxtrade.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.veloxtrade.platform.config.PlatformProperties;
import com.veloxtrade.platform.domain.MarketTick;
import com.veloxtrade.platform.dto.DepthView;
import com.veloxtrade.platform.dto.QuoteView;
import com.veloxtrade.platform.repository.MarketTickRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Polls the matching engine, persists the tick history, caches the latest quote
 * in Redis and fans the update out to every connected dashboard over STOMP.
 */
@Service
public class MarketDataService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataService.class);
    private static final int HISTORY_SIZE = 120;
    private static final String CACHE_KEY_PREFIX = "veloxtrade:quote:";

    private final EngineClient engineClient;
    private final SimpMessagingTemplate broker;
    private final MarketTickRepository tickRepository;
    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final ObjectMapper objectMapper;
    private final PlatformProperties properties;

    private final AtomicReference<QuoteView> latestQuote = new AtomicReference<>();
    private final AtomicReference<DepthView> latestDepth = new AtomicReference<>();
    private final Deque<BigDecimal> priceHistory = new ArrayDeque<>(HISTORY_SIZE);

    public MarketDataService(EngineClient engineClient,
                             SimpMessagingTemplate broker,
                             MarketTickRepository tickRepository,
                             ObjectProvider<StringRedisTemplate> redisProvider,
                             ObjectMapper objectMapper,
                             PlatformProperties properties) {
        this.engineClient = engineClient;
        this.broker = broker;
        this.tickRepository = tickRepository;
        this.redisProvider = redisProvider;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Scheduled(fixedRateString = "${veloxtrade.tick-interval}",
            initialDelayString = "${veloxtrade.tick-interval}")
    public void pollEngine() {
        try {
            EngineClient.EngineQuote quote = engineClient.quote();
            EngineClient.EngineDepth depth = engineClient.depth();
            publish(quote, depth);
        } catch (UpstreamUnavailableException ex) {
            // The dashboard keeps rendering the last known state while the engine restarts.
            log.warn("Skipping market tick: {}", ex.getMessage());
        }
    }

    private void publish(EngineClient.EngineQuote quote, EngineClient.EngineDepth depth) {
        QuoteView quoteView = new QuoteView(quote.symbol(), quote.price(), quote.bid(), quote.ask(),
                quote.restingOrders(), quote.timestamp());
        DepthView depthView = new DepthView(depth.symbol(), toLevels(depth.bids()),
                toLevels(depth.asks()), depth.timestamp());

        latestQuote.set(quoteView);
        latestDepth.set(depthView);
        synchronized (priceHistory) {
            if (priceHistory.size() == HISTORY_SIZE) {
                priceHistory.removeFirst();
            }
            priceHistory.addLast(quote.price());
        }

        tickRepository.save(new MarketTick(quote.symbol(), quote.price(), quote.bid(), quote.ask(),
                Instant.ofEpochMilli(quote.timestamp())));
        cache(quoteView);

        broker.convertAndSend("/topic/market/" + quoteView.symbol(), quoteView);
        broker.convertAndSend("/topic/depth/" + depthView.symbol(), depthView);
    }

    private static List<DepthView.Level> toLevels(List<EngineClient.EngineLevel> levels) {
        if (levels == null) {
            return List.of();
        }
        return levels.stream()
                .map(level -> new DepthView.Level(level.price(), level.quantity()))
                .toList();
    }

    private void cache(QuoteView quote) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            return;
        }
        try {
            redis.opsForValue().set(CACHE_KEY_PREFIX + quote.symbol(),
                    objectMapper.writeValueAsString(quote), java.time.Duration.ofMinutes(5));
        } catch (Exception ex) {
            log.debug("Quote cache write skipped: {}", ex.getMessage());
        }
    }

    public QuoteView currentQuote() {
        QuoteView cached = latestQuote.get();
        if (cached != null) {
            return cached;
        }
        return cachedQuote().orElseGet(() -> {
            EngineClient.EngineQuote quote = engineClient.quote();
            EngineClient.EngineDepth depth = engineClient.depth();
            publish(quote, depth);
            return latestQuote.get();
        });
    }

    public DepthView currentDepth() {
        DepthView cached = latestDepth.get();
        if (cached != null) {
            return cached;
        }
        publish(engineClient.quote(), engineClient.depth());
        return latestDepth.get();
    }

    private Optional<QuoteView> cachedQuote() {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            return Optional.empty();
        }
        try {
            String payload = redis.opsForValue().get(CACHE_KEY_PREFIX + properties.symbol());
            return payload == null
                    ? Optional.empty()
                    : Optional.of(objectMapper.readValue(payload, QuoteView.class));
        } catch (Exception ex) {
            log.debug("Quote cache read skipped: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /** Simple returns over the in-memory tick window, oldest first. */
    public List<Double> recentReturns() {
        List<BigDecimal> snapshot;
        synchronized (priceHistory) {
            snapshot = new ArrayList<>(priceHistory);
        }
        List<Double> returns = new ArrayList<>();
        for (int index = 1; index < snapshot.size(); index++) {
            double previous = snapshot.get(index - 1).doubleValue();
            if (previous > 0) {
                returns.add(snapshot.get(index).doubleValue() / previous - 1.0);
            }
        }
        return returns;
    }

    public List<MarketTick> history(String symbol, int limit) {
        return tickRepository.findBySymbolOrderByObservedAtDesc(symbol,
                org.springframework.data.domain.Limit.of(Math.clamp(limit, 1, 500)));
    }
}
