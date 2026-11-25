package com.sapidblue.ledger.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconciliationResponse {
    private Long accountId;
    private BigDecimal snapshotBalance;
    private BigDecimal computedBalance;
    private Boolean isMatched;
    private BigDecimal discrepancy;
    private LocalDateTime reconciledAt;
}
