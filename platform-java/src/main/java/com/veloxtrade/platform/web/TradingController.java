package com.veloxtrade.platform.web;

import com.veloxtrade.platform.dto.OrderReceipt;
import com.veloxtrade.platform.dto.PlaceOrderRequest;
import com.veloxtrade.platform.dto.PortfolioView;
import com.veloxtrade.platform.security.JwtService.AuthenticatedAccount;
import com.veloxtrade.platform.service.PortfolioService;
import com.veloxtrade.platform.service.TradingService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated trading endpoints for the signed-in account. */
@RestController
@RequestMapping("/api")
public class TradingController {

    private final TradingService tradingService;
    private final PortfolioService portfolioService;

    public TradingController(TradingService tradingService, PortfolioService portfolioService) {
        this.tradingService = tradingService;
        this.portfolioService = portfolioService;
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderReceipt placeOrder(@AuthenticationPrincipal AuthenticatedAccount account,
                                   @Valid @RequestBody PlaceOrderRequest request) {
        return tradingService.placeOrder(account.accountId(), request);
    }

    @GetMapping("/orders")
    public List<OrderReceipt> recentOrders(@AuthenticationPrincipal AuthenticatedAccount account,
                                           @RequestParam(defaultValue = "20") int limit) {
        return tradingService.recentOrders(account.accountId(), limit);
    }

    @GetMapping("/portfolio")
    public PortfolioView portfolio(@AuthenticationPrincipal AuthenticatedAccount account) {
        return portfolioService.snapshot(account.accountId());
    }
}
