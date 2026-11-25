package com.sapidblue.ledger.dto.response;

import com.sapidblue.ledger.enums.TransferStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferResponse {
    private Long id;
    private UUID transactionId;
    private Long sourceAccountId;
    private Long destinationAccountId;
    private BigDecimal amount;
    private String currency;
    private TransferStatus status;
    private String description;
    private LocalDateTime createdAt;
}
