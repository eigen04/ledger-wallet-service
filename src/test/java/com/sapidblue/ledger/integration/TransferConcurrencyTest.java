package com.sapidblue.ledger.integration;

import com.sapidblue.ledger.dto.request.CreateAccountRequest;
import com.sapidblue.ledger.dto.request.DepositRequest;
import com.sapidblue.ledger.dto.request.TransferRequest;
import com.sapidblue.ledger.dto.response.AccountResponse;
import com.sapidblue.ledger.exception.InsufficientBalanceException;
import com.sapidblue.ledger.service.AccountService;
import com.sapidblue.ledger.service.ReconciliationService;
import com.sapidblue.ledger.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TransferConcurrencyTest extends BaseIntegrationTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransferService transferService;

    @Autowired
    private ReconciliationService reconciliationService;

    private Long aliceId;
    private Long bobId;

    @BeforeEach
    void setUp() {
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

        transferService.deposit(new DepositRequest(aliceId, new BigDecimal("10000.0000"), "Initial deposit"));
        transferService.deposit(new DepositRequest(bobId, new BigDecimal("10000.0000"), "Initial deposit"));
    }

    @Test
    @DisplayName("Overdraft contention: exactly 4 of 10 concurrent 500 debits succeed against 2000 balance")
    void concurrentOverdraft_shouldEnforceExactSuccessAndFailureCounts() throws Exception {
        AccountResponse limited = accountService.createAccount(
                CreateAccountRequest.builder()
                        .accountHolderName("Limited Wallet")
                        .email("limited-" + System.nanoTime() + "@test.com")
                        .build());
        transferService.deposit(new DepositRequest(limited.getId(), new BigDecimal("2000.0000"), "Seed"));

        int threadCount = 10;
        BigDecimal debitAmount = new BigDecimal("500.0000");
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger insufficientBalanceCount = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                try {
                    transferService.executeTransfer(TransferRequest.builder()
                            .sourceAccountId(limited.getId())
                            .destinationAccountId(bobId)
                            .amount(debitAmount)
                            .description("Overdraft contention test")
                            .build());
                    successCount.incrementAndGet();
                } catch (InsufficientBalanceException expected) {
                    insufficientBalanceCount.incrementAndGet();
                }
                return null;
            }));
        }

        assertTrue(readyLatch.await(10, TimeUnit.SECONDS));
        startLatch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));

        for (Future<?> future : futures) {
            future.get();
        }

        assertEquals(4, successCount.get(), "Exactly 4 transfers should succeed");
        assertEquals(6, insufficientBalanceCount.get(), "Remaining 6 transfers must fail with InsufficientBalanceException");

        AccountResponse after = accountService.getAccountById(limited.getId());
        assertEquals(0, after.getAvailableBalance().compareTo(BigDecimal.ZERO));
        assertTrue(reconciliationService.reconcileAccount(limited.getId()).getIsMatched());
    }

    @Test
    @DisplayName("Bidirectional concurrent transfers complete without deadlock or balance drift")
    void bidirectionalTransfers_shouldNotDeadlock() throws Exception {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final boolean aliceToBob = (i % 2 == 0);
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                transferService.executeTransfer(TransferRequest.builder()
                        .sourceAccountId(aliceToBob ? aliceId : bobId)
                        .destinationAccountId(aliceToBob ? bobId : aliceId)
                        .amount(new BigDecimal("50.0000"))
                        .description("Bidirectional test")
                        .build());
                return null;
            }));
        }

        assertTrue(readyLatch.await(10, TimeUnit.SECONDS));
        startLatch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));

        for (Future<?> future : futures) {
            future.get();
        }

        AccountResponse alice = accountService.getAccountById(aliceId);
        AccountResponse bob = accountService.getAccountById(bobId);

        assertEquals(0, alice.getAvailableBalance().add(bob.getAvailableBalance())
                .compareTo(new BigDecimal("20000.0000")));
        assertTrue(reconciliationService.reconcileAccount(aliceId).getIsMatched());
        assertTrue(reconciliationService.reconcileAccount(bobId).getIsMatched());
    }
}
