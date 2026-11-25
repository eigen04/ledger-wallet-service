package com.sapidblue.ledger.service;

import com.sapidblue.ledger.dto.request.CreateAccountRequest;
import com.sapidblue.ledger.dto.response.AccountResponse;

import java.util.List;

public interface AccountService {

    AccountResponse createAccount(CreateAccountRequest request);

    AccountResponse getAccountById(Long id);

    AccountResponse getAccountByEmail(String email);

    List<AccountResponse> getAllAccounts();
}
