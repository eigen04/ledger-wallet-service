package com.sapidblue.ledger.entity;

import com.sapidblue.ledger.enums.AccountStatus;
import com.sapidblue.ledger.exception.InsufficientBalanceException;
import com.sapidblue.ledger.exception.InvalidTransferException;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_holder_name", nullable = false)
    private String accountHolderName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "available_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal availableBalance;

    @Column(name = "locked_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal lockedBalance;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.availableBalance == null) this.availableBalance = BigDecimal.ZERO;
        if (this.lockedBalance == null) this.lockedBalance = BigDecimal.ZERO;
        if (this.currency == null) this.currency = "INR";
        if (this.status == null) this.status = AccountStatus.ACTIVE;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void debit(BigDecimal amount, String transferCurrency) {
        validateTransactable(amount, transferCurrency);
        if (this.availableBalance.compareTo(amount) < 0) {
            throw new InsufficientBalanceException(
                    String.format("Account %d has insufficient balance. Available: %s, Required: %s",
                            this.id, this.availableBalance, amount));
        }
        this.availableBalance = this.availableBalance.subtract(amount);
    }

    public void credit(BigDecimal amount, String transferCurrency) {
        validateTransactable(amount, transferCurrency);
        this.availableBalance = this.availableBalance.add(amount);
    }

    private void validateTransactable(BigDecimal amount, String transferCurrency) {
        if (this.status != AccountStatus.ACTIVE) {
            throw new InvalidTransferException(
                    String.format("Account %d is %s and cannot transact", this.id, this.status));
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransferException("Amount must be positive");
        }
        if (transferCurrency == null || !this.currency.equalsIgnoreCase(transferCurrency)) {
            throw new InvalidTransferException(
                    String.format("Currency mismatch for account %d: expected %s, got %s",
                            this.id, this.currency, transferCurrency));
        }
    }
}
