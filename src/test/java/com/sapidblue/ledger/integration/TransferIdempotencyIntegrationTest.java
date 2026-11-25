package com.sapidblue.ledger.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sapidblue.ledger.dto.request.CreateAccountRequest;
import com.sapidblue.ledger.dto.request.DepositRequest;
import com.sapidblue.ledger.dto.request.TransferRequest;
import com.sapidblue.ledger.dto.response.AccountResponse;
import com.sapidblue.ledger.dto.response.ApiErrorResponse;
import com.sapidblue.ledger.dto.response.TransferResponse;
import com.sapidblue.ledger.entity.IdempotencyRecord;
import com.sapidblue.ledger.enums.IdempotencyStatus;
import com.sapidblue.ledger.repository.IdempotencyRecordRepository;
import com.sapidblue.ledger.service.AccountService;
import com.sapidblue.ledger.service.TransferService;
import com.sapidblue.ledger.util.HashUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TransferIdempotencyIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransferService transferService;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private Long sourceId;
    private Long destId;

    @BeforeEach
    void setUp() {
        AccountResponse source = accountService.createAccount(
                CreateAccountRequest.builder()
                        .accountHolderName("Source User")
                        .email("src-" + System.nanoTime() + "@test.com")
                        .build());
        AccountResponse dest = accountService.createAccount(
                CreateAccountRequest.builder()
                        .accountHolderName("Dest User")
                        .email("dst-" + System.nanoTime() + "@test.com")
                        .build());

        sourceId = source.getId();
        destId = dest.getId();

        transferService.deposit(new DepositRequest(sourceId, new BigDecimal("5000.0000"), "Seed"));
    }

    @Test
    @DisplayName("Idempotency replay with identical body returns cached response without double-debit")
    void idempotentReplay_shouldReturnCachedResponseWithoutDoubleDebit() {
        String key = "idem-" + UUID.randomUUID();
        TransferRequest body = TransferRequest.builder()
                .sourceAccountId(sourceId)
                .destinationAccountId(destId)
                .amount(new BigDecimal("1000.0000"))
                .description("Idempotent payment")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", key);
        HttpEntity<TransferRequest> entity = new HttpEntity<>(body, headers);

        ResponseEntity<TransferResponse> first = restTemplate.postForEntity(
                "/api/v1/transfers", entity, TransferResponse.class);
        ResponseEntity<TransferResponse> second = restTemplate.postForEntity(
                "/api/v1/transfers", entity, TransferResponse.class);

        assertEquals(HttpStatus.CREATED, first.getStatusCode());
        assertEquals(HttpStatus.CREATED, second.getStatusCode());
        assertNotNull(first.getBody());
        assertNotNull(second.getBody());
        assertEquals(first.getBody().getTransactionId(), second.getBody().getTransactionId());

        AccountResponse sourceAfter = accountService.getAccountById(sourceId);
        assertEquals(0, sourceAfter.getAvailableBalance().compareTo(new BigDecimal("4000.0000")));
    }

    @Test
    @DisplayName("Idempotency key reused with mutated payload returns HTTP 422")
    void idempotencyPayloadMismatch_shouldReturn422() {
        String key = "idem-tamper-" + UUID.randomUUID();
        TransferRequest original = TransferRequest.builder()
                .sourceAccountId(sourceId)
                .destinationAccountId(destId)
                .amount(new BigDecimal("100.0000"))
                .description("Original")
                .build();

        TransferRequest mutated = TransferRequest.builder()
                .sourceAccountId(sourceId)
                .destinationAccountId(destId)
                .amount(new BigDecimal("999.0000"))
                .description("Tampered")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", key);

        ResponseEntity<TransferResponse> first = restTemplate.postForEntity(
                "/api/v1/transfers", new HttpEntity<>(original, headers), TransferResponse.class);
        assertEquals(HttpStatus.CREATED, first.getStatusCode());

        ResponseEntity<ApiErrorResponse> second = restTemplate.postForEntity(
                "/api/v1/transfers", new HttpEntity<>(mutated, headers), ApiErrorResponse.class);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, second.getStatusCode());
    }

    @Test
    @DisplayName("Concurrent duplicate request while IN_PROGRESS lease is active returns HTTP 409")
    void concurrentDuplicateInProgress_shouldReturn409() throws Exception {
        String key = "idem-inflight-" + UUID.randomUUID();
        TransferRequest body = TransferRequest.builder()
                .sourceAccountId(sourceId)
                .destinationAccountId(destId)
                .amount(new BigDecimal("250.0000"))
                .description("In-flight")
                .build();

        String rawJson = objectMapper.writeValueAsString(body);
        idempotencyRecordRepository.save(IdempotencyRecord.builder()
                .idempotencyKey(key)
                .requestHash(HashUtil.sha256(rawJson))
                .status(IdempotencyStatus.IN_PROGRESS)
                .lockedUntil(Instant.now().plusSeconds(30))
                .build());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", key);

        ResponseEntity<ApiErrorResponse> response = restTemplate.postForEntity(
                "/api/v1/transfers", new HttpEntity<>(rawJson, headers), ApiErrorResponse.class);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    @Test
    @DisplayName("FROZEN and CLOSED accounts are rejected from transacting")
    void frozenAndClosedAccounts_shouldBeRejected() {
        jdbcTemplate.update("UPDATE accounts SET status = 'FROZEN' WHERE id = ?", sourceId);

        TransferRequest request = TransferRequest.builder()
                .sourceAccountId(sourceId)
                .destinationAccountId(destId)
                .amount(new BigDecimal("100.0000"))
                .description("Should fail")
                .build();

        ResponseEntity<ApiErrorResponse> frozenResp = restTemplate.postForEntity(
                "/api/v1/transfers", request, ApiErrorResponse.class);
        assertEquals(HttpStatus.BAD_REQUEST, frozenResp.getStatusCode());

        jdbcTemplate.update("UPDATE accounts SET status = 'ACTIVE' WHERE id = ?", sourceId);
        jdbcTemplate.update("UPDATE accounts SET status = 'CLOSED' WHERE id = ?", destId);

        ResponseEntity<ApiErrorResponse> closedResp = restTemplate.postForEntity(
                "/api/v1/transfers", request, ApiErrorResponse.class);
        assertEquals(HttpStatus.BAD_REQUEST, closedResp.getStatusCode());
    }
}
