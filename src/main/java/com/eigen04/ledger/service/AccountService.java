package com.eigen04.ledger.service;

import com.eigen04.ledger.dto.request.CreateAccountRequest;
import com.eigen04.ledger.dto.response.AccountResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AccountService {

    AccountResponse createAccount(CreateAccountRequest request);

    AccountResponse getAccountById(Long id);

    AccountResponse getAccountByEmail(String email);

    Page<AccountResponse> getAllAccounts(Pageable pageable);
}
