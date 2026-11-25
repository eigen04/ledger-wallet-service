package com.eigen04.ledger.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.eigen04.ledger.dto.request.CreateAccountRequest;
import com.eigen04.ledger.dto.request.DepositRequest;
import com.eigen04.ledger.dto.request.TransferRequest;
import com.eigen04.ledger.dto.response.AccountResponse;
import com.eigen04.ledger.dto.response.ApiErrorResponse;
import com.eigen04.ledger.dto.response.TransferResponse;
import com.eigen04.ledger.entity.IdempotencyRecord;
import com.eigen04.ledger.enums.IdempotencyStatus;
import com.eigen04.ledger.repository.IdempotencyRecordRepository;
import com.eigen04.ledger.service.AccountService;
import com.eigen04.ledger.service.TransferService;
import com.eigen04.ledger.util.HashUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

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
                .idempotencyKey("POST:/api/v1/transfers:" + key)
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

    @Test
    @DisplayName("Expired IN_PROGRESS lease is reclaimed and processed cleanly")
    void expiredLease_shouldBeReclaimedAndProcessed() throws Exception {
        String key = "idem-expired-" + UUID.randomUUID();
        TransferRequest body = TransferRequest.builder()
                .sourceAccountId(sourceId)
                .destinationAccountId(destId)
                .amount(new BigDecimal("300.0000"))
                .description("Expired lease recovery")
                .build();

        String rawJson = objectMapper.writeValueAsString(body);
        idempotencyRecordRepository.save(IdempotencyRecord.builder()
                .idempotencyKey("POST:/api/v1/transfers:" + key)
                .requestHash(HashUtil.sha256(rawJson))
                .status(IdempotencyStatus.IN_PROGRESS)
                .lockedUntil(Instant.now().minusSeconds(5))
                .build());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", key);

        ResponseEntity<TransferResponse> response = restTemplate.postForEntity(
                "/api/v1/transfers", new HttpEntity<>(rawJson, headers), TransferResponse.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        IdempotencyRecord updated = idempotencyRecordRepository
                .findByIdempotencyKey("POST:/api/v1/transfers:" + key).orElseThrow();
        assertEquals(IdempotencyStatus.COMPLETED, updated.getStatus());
    }

    @Test
    @DisplayName("FAILED idempotency key allows safe retry")
    void failedKey_shouldAllowRetry() throws Exception {
        String key = "idem-failed-" + UUID.randomUUID();
        TransferRequest body = TransferRequest.builder()
                .sourceAccountId(sourceId)
                .destinationAccountId(destId)
                .amount(new BigDecimal("400.0000"))
                .description("Retry after failure")
                .build();

        String rawJson = objectMapper.writeValueAsString(body);
        idempotencyRecordRepository.save(IdempotencyRecord.builder()
                .idempotencyKey("POST:/api/v1/transfers:" + key)
                .requestHash(HashUtil.sha256(rawJson))
                .status(IdempotencyStatus.FAILED)
                .build());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", key);

        ResponseEntity<TransferResponse> response = restTemplate.postForEntity(
                "/api/v1/transfers", new HttpEntity<>(rawJson, headers), TransferResponse.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        IdempotencyRecord updated = idempotencyRecordRepository
                .findByIdempotencyKey("POST:/api/v1/transfers:" + key).orElseThrow();
        assertEquals(IdempotencyStatus.COMPLETED, updated.getStatus());
    }

    @Test
    @DisplayName("Concurrent HTTP requests with identical Idempotency-Key debit only once")
    void concurrentHttpRequestsWithSameKey_shouldDebitOnlyOnce() throws Exception {
        String key = "idem-race-" + UUID.randomUUID();
        TransferRequest body = TransferRequest.builder()
                .sourceAccountId(sourceId)
                .destinationAccountId(destId)
                .amount(new BigDecimal("500.0000"))
                .description("HTTP race")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", key);
        HttpEntity<TransferRequest> entity = new HttpEntity<>(body, headers);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ResponseEntity<String>>> futures = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return restTemplate.postForEntity("/api/v1/transfers", entity, String.class);
            }));
        }

        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));

        int createdCount = 0;
        int conflictCount = 0;
        for (Future<ResponseEntity<String>> f : futures) {
            HttpStatusCode status = f.get().getStatusCode();
            if (status == HttpStatus.CREATED) createdCount++;
            else if (status == HttpStatus.CONFLICT) conflictCount++;
            else fail("Unexpected status: " + status);
        }

        assertTrue(createdCount >= 1 && (createdCount + conflictCount) == 2);
        AccountResponse sourceAfter = accountService.getAccountById(sourceId);
        assertEquals(0, sourceAfter.getAvailableBalance().compareTo(new BigDecimal("4500.0000")));
    }

    @Test
    @DisplayName("Same Idempotency-Key across different routes does not collide")
    void sameKeyAcrossDifferentRoutes_shouldNotCollide() {
        String key = "idem-route-" + UUID.randomUUID();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", key);

        DepositRequest deposit = new DepositRequest(sourceId, new BigDecimal("100.0000"), "Route A");
        TransferRequest transfer = TransferRequest.builder()
                .sourceAccountId(sourceId)
                .destinationAccountId(destId)
                .amount(new BigDecimal("100.0000"))
                .description("Route B")
                .build();

        ResponseEntity<TransferResponse> depositResp = restTemplate.postForEntity(
                "/api/v1/transfers/deposit", new HttpEntity<>(deposit, headers), TransferResponse.class);
        ResponseEntity<TransferResponse> transferResp = restTemplate.postForEntity(
                "/api/v1/transfers", new HttpEntity<>(transfer, headers), TransferResponse.class);

        assertEquals(HttpStatus.CREATED, depositResp.getStatusCode());
        assertEquals(HttpStatus.CREATED, transferResp.getStatusCode());
    }

    @Test
    @DisplayName("Cross-currency transfer is rejected with HTTP 400")
    void crossCurrencyTransfer_shouldBeRejected() {
        AccountResponse usdAccount = accountService.createAccount(
                CreateAccountRequest.builder()
                        .accountHolderName("USD Holder")
                        .email("usd-" + System.nanoTime() + "@test.com")
                        .currency("USD")
                        .build());

        TransferRequest request = TransferRequest.builder()
                .sourceAccountId(sourceId)
                .destinationAccountId(usdAccount.getId())
                .amount(new BigDecimal("100.0000"))
                .description("Cross-currency")
                .build();

        ResponseEntity<ApiErrorResponse> response = restTemplate.postForEntity(
                "/api/v1/transfers", request, ApiErrorResponse.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}
