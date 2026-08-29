package com.veloxtrade.platform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A trading account: login identity plus settled cash balance. */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 120)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Column(name = "cash_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal cashBalance;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Account() {
        // required by JPA
    }

    public Account(String email, String passwordHash, String displayName, BigDecimal openingCash) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.cashBalance = openingCash;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public BigDecimal getCashBalance() {
        return cashBalance;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void debit(BigDecimal amount) {
        this.cashBalance = this.cashBalance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        this.cashBalance = this.cashBalance.add(amount);
    }
}
