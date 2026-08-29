package com.veloxtrade.platform.service;

import com.veloxtrade.platform.config.PlatformProperties;
import com.veloxtrade.platform.domain.Account;
import com.veloxtrade.platform.domain.OrderSide;
import com.veloxtrade.platform.domain.OrderStatus;
import com.veloxtrade.platform.domain.Position;
import com.veloxtrade.platform.domain.TradeOrder;
import com.veloxtrade.platform.dto.OrderReceipt;
import com.veloxtrade.platform.dto.PlaceOrderRequest;
import com.veloxtrade.platform.repository.AccountRepository;
import com.veloxtrade.platform.repository.PositionRepository;
import com.veloxtrade.platform.repository.TradeOrderRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies risk checks, routes orders to the matching engine and books the
 * resulting fills against the account's cash and positions.
 */
@Service
public class TradingService {

    private final AccountRepository accountRepository;
    private final PositionRepository positionRepository;
    private final TradeOrderRepository orderRepository;
    private final EngineClient engineClient;
    private final PlatformProperties properties;
    private final Timer matchLatencyTimer;

    public TradingService(AccountRepository accountRepository,
                          PositionRepository positionRepository,
                          TradeOrderRepository orderRepository,
                          EngineClient engineClient,
                          PlatformProperties properties,
                          MeterRegistry meterRegistry) {
        this.accountRepository = accountRepository;
        this.positionRepository = positionRepository;
        this.orderRepository = orderRepository;
        this.engineClient = engineClient;
        this.properties = properties;
        this.matchLatencyTimer = Timer.builder("veloxtrade.engine.match.latency")
                .description("Time the C++ engine spent matching a submitted order")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
    }

    @Transactional
    public OrderReceipt placeOrder(UUID accountId, PlaceOrderRequest request) {
        if (!properties.symbol().equalsIgnoreCase(request.symbol())) {
            throw new TradingRuleException(
                    "Only " + properties.symbol() + " is listed on this simulated venue");
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new TradingRuleException("Account no longer exists"));
        OrderSide side = OrderSide.valueOf(request.side());
        Position position = positionRepository
                .findByAccountIdAndSymbol(accountId, properties.symbol())
                .orElseGet(() -> new Position(accountId, properties.symbol()));

        BigDecimal notional = request.limitPrice().multiply(BigDecimal.valueOf(request.quantity()));
        if (side == OrderSide.BUY && account.getCashBalance().compareTo(notional) < 0) {
            throw new TradingRuleException("Insufficient buying power for this order");
        }
        if (side == OrderSide.SELL && position.getQuantity() < request.quantity()) {
            throw new TradingRuleException("Short selling is disabled on this simulated venue");
        }

        EngineClient.EngineOrderResult result =
                engineClient.submit(side, request.quantity(), request.limitPrice());
        matchLatencyTimer.record(result.matchLatencyNanos(), TimeUnit.NANOSECONDS);

        BigDecimal cashMoved = BigDecimal.ZERO;
        long filled = 0L;
        for (EngineClient.EngineFill fill : result.fills() == null ? List.<EngineClient.EngineFill>of() : result.fills()) {
            cashMoved = cashMoved.add(fill.price().multiply(BigDecimal.valueOf(fill.quantity())));
            filled += fill.quantity();
            position.apply(side, fill.quantity(), fill.price());
        }

        BigDecimal averageFillPrice = filled == 0
                ? null
                : cashMoved.divide(BigDecimal.valueOf(filled), 4, RoundingMode.HALF_UP);

        if (filled > 0) {
            if (side == OrderSide.BUY) {
                account.debit(cashMoved);
            } else {
                account.credit(cashMoved);
            }
            positionRepository.save(position);
            accountRepository.save(account);
        }

        TradeOrder order = new TradeOrder(accountId, properties.symbol(), side, request.quantity(),
                request.limitPrice(), OrderStatus.valueOf(result.status()), filled,
                averageFillPrice, result.orderId(), result.matchLatencyNanos());
        return OrderReceipt.from(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public List<OrderReceipt> recentOrders(UUID accountId, int limit) {
        return orderRepository
                .findByAccountIdOrderByCreatedAtDesc(accountId, Limit.of(Math.clamp(limit, 1, 100)))
                .stream()
                .map(OrderReceipt::from)
                .toList();
    }
}
