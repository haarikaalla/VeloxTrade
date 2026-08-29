package com.veloxtrade.platform.dto;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        String email,
        String displayName) {
}
