package com.sapidblue.ledger.controller;

import com.sapidblue.ledger.dto.request.CreateAccountRequest;
import com.sapidblue.ledger.dto.response.AccountResponse;
import com.sapidblue.ledger.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(
            @Valid @RequestBody CreateAccountRequest request) {
        log.info("POST /api/v1/accounts - Creating account for: {}", request.getEmail());
        AccountResponse response = accountService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable Long id) {
        log.info("GET /api/v1/accounts/{}", id);
        return ResponseEntity.ok(accountService.getAccountById(id));
    }

    @GetMapping
    public ResponseEntity<List<AccountResponse>> getAllAccounts() {
        log.info("GET /api/v1/accounts");
        return ResponseEntity.ok(accountService.getAllAccounts());
    }

    @GetMapping("/email/{email}")
    public ResponseEntity<AccountResponse> getAccountByEmail(@PathVariable String email) {
        log.info("GET /api/v1/accounts/email/{}", email);
        return ResponseEntity.ok(accountService.getAccountByEmail(email));
    }
}
