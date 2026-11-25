package com.sapidblue.ledger.dto.response;

import com.sapidblue.ledger.enums.AccountStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
