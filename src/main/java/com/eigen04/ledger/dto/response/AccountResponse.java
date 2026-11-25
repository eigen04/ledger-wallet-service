package com.eigen04.ledger.dto.response;

import com.eigen04.ledger.enums.AccountStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountResponse {
    private Long id;
    private String accountHolderName;
    private String email;
    private BigDecimal availableBalance;
    private BigDecimal lockedBalance;
    private String currency;
    private AccountStatus status;
    private Instant createdAt;
    private Instant updatedAt;
}
