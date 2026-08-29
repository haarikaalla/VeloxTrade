package com.veloxtrade.platform.domain;

/** Lifecycle state reported by the matching engine. */
public enum OrderStatus {
    FILLED,
    PARTIALLY_FILLED,
    RESTING,
    REJECTED
}
