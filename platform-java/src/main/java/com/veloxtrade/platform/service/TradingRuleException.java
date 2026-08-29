package com.veloxtrade.platform.service;

/** Raised when a request is well formed but violates a trading rule. */
public class TradingRuleException extends RuntimeException {

    public TradingRuleException(String message) {
        super(message);
    }
}
