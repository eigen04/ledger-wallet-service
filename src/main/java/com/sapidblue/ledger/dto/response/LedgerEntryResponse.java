package com.sapidblue.ledger.dto.response;

import com.sapidblue.ledger.enums.EntryType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerEntryResponse {
    private Long id;
    private UUID transactionId;
    private Long accountId;
    private EntryType entryType;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String description;
    private LocalDateTime createdAt;
}
