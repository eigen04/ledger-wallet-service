package com.sapidblue.ledger.service;

import com.sapidblue.ledger.dto.request.CreateAccountRequest;
import com.sapidblue.ledger.dto.response.AccountResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AccountService {

    AccountResponse createAccount(CreateAccountRequest request);

    AccountResponse getAccountById(Long id);

    AccountResponse getAccountByEmail(String email);

    Page<AccountResponse> getAllAccounts(Pageable pageable);
}
