package com.eigen04.ledger.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "reconciliation_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconciliationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "snapshot_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal snapshotBalance;

    @Column(name = "computed_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal computedBalance;

    @Column(name = "is_matched", nullable = false)
    private Boolean isMatched;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal discrepancy;

    @Column(name = "reconciled_at", nullable = false, updatable = false)
    private Instant reconciledAt;

    @PrePersist
    protected void onCreate() {
        this.reconciledAt = Instant.now();
        if (this.discrepancy == null) this.discrepancy = BigDecimal.ZERO;
    }
}
