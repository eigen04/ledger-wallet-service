package com.sapidblue.ledger.service.impl;

import com.sapidblue.ledger.dto.request.CreateAccountRequest;
import com.sapidblue.ledger.dto.response.AccountResponse;
import com.sapidblue.ledger.entity.Account;
import com.sapidblue.ledger.enums.AccountStatus;
import com.sapidblue.ledger.exception.AccountNotFoundException;
import com.sapidblue.ledger.exception.DuplicateAccountException;
import com.sapidblue.ledger.repository.AccountRepository;
import com.sapidblue.ledger.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;

    @Override
    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        log.info("Creating account for: {}", request.getEmail());

        if (accountRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateAccountException(request.getEmail());
        }

        Account account = Account.builder()
                .accountHolderName(request.getAccountHolderName())
                .email(request.getEmail())
                .availableBalance(BigDecimal.ZERO)
                .lockedBalance(BigDecimal.ZERO)
                .currency(request.getCurrency() != null ? request.getCurrency() : "INR")
                .status(AccountStatus.ACTIVE)
                .build();

        Account saved = accountRepository.save(account);
        log.info("Account created with ID: {}", saved.getId());
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getAccountById(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
        return toResponse(account);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getAccountByEmail(String email) {
        Account account = accountRepository.findByEmail(email)
                .orElseThrow(() -> new AccountNotFoundException("Account not found with email: " + email));
        return toResponse(account);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountResponse> getAllAccounts() {
        return accountRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    private AccountResponse toResponse(Account account) {
        return AccountResponse.builder()
                .id(account.getId())
                .accountHolderName(account.getAccountHolderName())
                .email(account.getEmail())
                .availableBalance(account.getAvailableBalance())
                .lockedBalance(account.getLockedBalance())
                .currency(account.getCurrency())
                .status(account.getStatus())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .build();
    }
}
