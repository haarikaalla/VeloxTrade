package com.veloxtrade.platform.service;

import com.veloxtrade.platform.domain.Account;
import com.veloxtrade.platform.domain.Position;
import com.veloxtrade.platform.dto.PortfolioView;
import com.veloxtrade.platform.repository.AccountRepository;
import com.veloxtrade.platform.repository.PositionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Marks positions to the live simulated price and reports account equity. */
@Service
public class PortfolioService {

    private final AccountRepository accountRepository;
    private final PositionRepository positionRepository;
    private final MarketDataService marketDataService;

    public PortfolioService(AccountRepository accountRepository,
                            PositionRepository positionRepository,
                            MarketDataService marketDataService) {
        this.accountRepository = accountRepository;
        this.positionRepository = positionRepository;
        this.marketDataService = marketDataService;
    }

    @Transactional(readOnly = true)
    public PortfolioView snapshot(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new TradingRuleException("Account no longer exists"));
        BigDecimal lastPrice = marketDataService.currentQuote().price();

        List<PortfolioView.PositionView> views = positionRepository.findByAccountId(accountId)
                .stream()
                .filter(position -> position.getQuantity() != 0)
                .map(position -> toView(position, lastPrice))
                .toList();

        BigDecimal positionsValue = views.stream()
                .map(PortfolioView.PositionView::marketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal unrealised = views.stream()
                .map(PortfolioView.PositionView::unrealizedPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new PortfolioView(
                account.getDisplayName(),
                scale(account.getCashBalance()),
                scale(positionsValue),
                scale(account.getCashBalance().add(positionsValue)),
                scale(unrealised),
                views);
    }

    private static PortfolioView.PositionView toView(Position position, BigDecimal lastPrice) {
        BigDecimal quantity = BigDecimal.valueOf(position.getQuantity());
        BigDecimal marketValue = lastPrice.multiply(quantity);
        BigDecimal costBasis = position.getAveragePrice().multiply(quantity);
        return new PortfolioView.PositionView(
                position.getSymbol(),
                position.getQuantity(),
                scale(position.getAveragePrice()),
                scale(lastPrice),
                scale(marketValue),
                scale(marketValue.subtract(costBasis)));
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
