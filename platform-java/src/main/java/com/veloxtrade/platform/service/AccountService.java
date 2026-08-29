package com.veloxtrade.platform.service;

import com.veloxtrade.platform.config.PlatformProperties;
import com.veloxtrade.platform.domain.Account;
import com.veloxtrade.platform.dto.AuthResponse;
import com.veloxtrade.platform.dto.LoginRequest;
import com.veloxtrade.platform.dto.RegisterRequest;
import com.veloxtrade.platform.repository.AccountRepository;
import com.veloxtrade.platform.security.JwtService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registration and login for simulated trading accounts. */
@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PlatformProperties properties;
    /** Valid hash of a random value, compared against when an email is unknown. */
    private final String decoyHash;

    public AccountService(AccountRepository accountRepository,
                          PasswordEncoder passwordEncoder,
                          JwtService jwtService,
                          PlatformProperties properties) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.properties = properties;
        this.decoyHash = passwordEncoder.encode(java.util.UUID.randomUUID().toString());
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (accountRepository.existsByEmailIgnoreCase(email)) {
            throw new TradingRuleException("An account with that email already exists");
        }
        Account account = accountRepository.save(new Account(email,
                passwordEncoder.encode(request.password()),
                request.displayName().trim(),
                properties.openingCash()));
        return toAuthResponse(account);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Account account = accountRepository
                .findByEmailIgnoreCase(request.email().trim())
                .orElse(null);
        // Always run a hash comparison so a missing account and a wrong password
        // take the same amount of time (avoids user enumeration by timing).
        String storedHash = account == null ? decoyHash : account.getPasswordHash();
        boolean matches = passwordEncoder.matches(request.password(), storedHash);
        if (account == null || !matches) {
            throw new BadCredentialsException("Invalid email or password");
        }
        return toAuthResponse(account);
    }

    private AuthResponse toAuthResponse(Account account) {
        return new AuthResponse(
                jwtService.issueToken(account.getId(), account.getEmail()),
                "Bearer",
                jwtService.tokenTtlSeconds(),
                account.getEmail(),
                account.getDisplayName());
    }
}
