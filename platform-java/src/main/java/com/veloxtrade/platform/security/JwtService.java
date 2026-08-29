package com.veloxtrade.platform.security;

import com.veloxtrade.platform.config.PlatformProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Issues and validates the stateless HS256 access tokens used by the dashboard. */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final int MINIMUM_SECRET_BYTES = 32;

    private final SecretKey signingKey;
    private final java.time.Duration tokenTtl;

    public JwtService(PlatformProperties properties) {
        this.tokenTtl = properties.security().tokenTtl();
        this.signingKey = resolveKey(properties.security().jwtSecret());
    }

    private static SecretKey resolveKey(String configuredSecret) {
        byte[] material = configuredSecret == null
                ? new byte[0]
                : configuredSecret.getBytes(StandardCharsets.UTF_8);
        if (material.length < MINIMUM_SECRET_BYTES) {
            // Never fall back to a hard-coded key: generate an ephemeral one instead so a
            // misconfigured deployment invalidates tokens rather than trusting a known secret.
            log.warn("VELOXTRADE_JWT_SECRET is missing or shorter than {} bytes; "
                    + "generating an ephemeral key. Tokens will not survive a restart.",
                    MINIMUM_SECRET_BYTES);
            material = new byte[64];
            new SecureRandom().nextBytes(material);
        }
        return Keys.hmacShaKeyFor(material);
    }

    public String issueToken(UUID accountId, String email) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(accountId.toString())
                .claim("email", email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(tokenTtl)))
                .signWith(signingKey)
                .compact();
    }

    public Optional<AuthenticatedAccount> verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(new AuthenticatedAccount(
                    UUID.fromString(claims.getSubject()), claims.get("email", String.class)));
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected access token: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public long tokenTtlSeconds() {
        return tokenTtl.toSeconds();
    }

    /** Principal placed on the security context for authenticated requests. */
    public record AuthenticatedAccount(UUID accountId, String email) {
    }
}
