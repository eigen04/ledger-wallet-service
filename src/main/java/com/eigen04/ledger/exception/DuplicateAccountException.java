package com.eigen04.ledger.exception;

public class DuplicateAccountException extends RuntimeException {
    public DuplicateAccountException(String email) {
        super("Account already exists with email: " + email);
    }
}
