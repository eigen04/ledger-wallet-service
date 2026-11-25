package com.sapidblue.ledger.integration;

import com.sapidblue.ledger.dto.request.CreateAccountRequest;
import com.sapidblue.ledger.dto.request.TransferRequest;
import com.sapidblue.ledger.dto.response.AccountResponse;
import com.sapidblue.ledger.dto.response.TransferResponse;
import com.sapidblue.ledger.service.AccountService;
import com.sapidblue.ledger.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Multithreaded Concurrency Stress Test.
 *
 * Validates that:
 * 1. Concurrent transfers do NOT cause double-spend or negative balances
 * 2. Pessimistic locking correctly serializes conflicting writes
 * 3. Deterministic lock ordering prevents deadlocks
 * 4. Final balances are mathematically consistent
 */
class TransferConcurrencyTest extends BaseIntegrationTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransferService transferService;

    private Long aliceId;
    private Long bobId;

    @BeforeEach
    void setUp() {
        // Create fresh accounts for each test
        AccountResponse alice = accountService.createAccount(
                CreateAccountRequest.builder()
                        .accountHolderName("Alice Test")
                        .email("alice-" + System.nanoTime() + "@test.com")
                        .build());

        AccountResponse bob = accountService.createAccount(
                CreateAccountRequest.builder()
                        .accountHolderName("Bob Test")
                        .email("bob-" + System.nanoTime() + "@test.com")
                        .build());

        aliceId = alice.getId();
        bobId = bob.getId();

        // Deposit initial funds
        transferService.deposit(new com.sapidblue.ledger.dto.request.DepositRequest(
                aliceId, new BigDecimal("10000.0000"), "Initial deposit"));
        transferService.deposit(new com.sapidblue.ledger.dto.request.DepositRequest(
                bobId, new BigDecimal("10000.0000"), "Initial deposit"));
    }

    @Test
    @DisplayName("50 concurrent transfers should NOT cause double-spend")
    void concurrentTransfers_shouldNotCauseDoubleSpend() throws Exception {
        int threadCount = 50;
        BigDecimal transferAmount = new BigDecimal("100.0000");

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Future<Boolean>> futures = new ArrayList<>();

        // All 50 threads try to transfer 100 from Alice to Bob simultaneously
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    latch.countDown();
                    latch.await(); // Wait for all threads to be ready
                    transferService.executeTransfer(TransferRequest.builder()
                            .sourceAccountId(aliceId)
                            .destinationAccountId(bobId)
                            .amount(transferAmount)
                            .description("Concurrent transfer test")
                            .build());
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }));
        }

        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        // Count successful transfers
        long successCount = futures.stream()
                .map(f -> {
                    try { return f.get(); } catch (Exception e) { return false; }
                })
                .filter(Boolean::booleanValue)
                .count();

        // Verify balances
        AccountResponse alice = accountService.getAccountById(aliceId);
        AccountResponse bob = accountService.getAccountById(bobId);

        BigDecimal totalDeducted = transferAmount.multiply(BigDecimal.valueOf(successCount));

        // Alice's balance should be exactly: 10000 - (100 * successCount)
        assertEquals(
                new BigDecimal("10000.0000").subtract(totalDeducted),
                alice.getAvailableBalance(),
                "Alice's balance should be exactly 10000 - deducted amount"
        );

        // Bob's balance should be exactly: 10000 + (100 * successCount)
        assertEquals(
                new BigDecimal("10000.0000").add(totalDeducted),
                bob.getAvailableBalance(),
                "Bob's balance should be exactly 10000 + credited amount"
        );

        // Total money in system must remain constant (conservation)
        BigDecimal totalBalance = alice.getAvailableBalance().add(bob.getAvailableBalance());
        assertEquals(
                new BigDecimal("20000.0000"),
                totalBalance,
                "Total money in the system must be conserved"
        );

        // Balance should NEVER be negative
        assertTrue(alice.getAvailableBalance().compareTo(BigDecimal.ZERO) >= 0,
                "Alice's balance should never be negative");
        assertTrue(bob.getAvailableBalance().compareTo(BigDecimal.ZERO) >= 0,
                "Bob's balance should never be negative");
    }

    @Test
    @DisplayName("Bidirectional concurrent transfers should NOT deadlock")
    void bidirectionalTransfers_shouldNotDeadlock() throws Exception {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Future<Boolean>> futures = new ArrayList<>();

        // Half threads: Alice -> Bob, Half threads: Bob -> Alice
        for (int i = 0; i < threadCount; i++) {
            final boolean aliceToBob = (i % 2 == 0);
            futures.add(executor.submit(() -> {
                try {
                    latch.countDown();
                    latch.await();

                    TransferRequest request = TransferRequest.builder()
                            .sourceAccountId(aliceToBob ? aliceId : bobId)
                            .destinationAccountId(aliceToBob ? bobId : aliceId)
                            .amount(new BigDecimal("50.0000"))
                            .description("Bidirectional test")
                            .build();

                    transferService.executeTransfer(request);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }));
        }

        executor.shutdown();
        boolean finished = executor.awaitTermination(30, TimeUnit.SECONDS);
        assertTrue(finished, "All transfers should complete without deadlock within 30 seconds");

        // Verify total money conservation
        AccountResponse alice = accountService.getAccountById(aliceId);
        AccountResponse bob = accountService.getAccountById(bobId);

        BigDecimal totalBalance = alice.getAvailableBalance().add(bob.getAvailableBalance());
        assertEquals(
                new BigDecimal("20000.0000"),
                totalBalance,
                "Total money must be conserved even with bidirectional transfers"
        );
    }
}
