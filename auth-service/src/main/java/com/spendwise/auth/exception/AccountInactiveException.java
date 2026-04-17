package com.spendwise.auth.exception;

public class AccountInactiveException extends RuntimeException {

    public AccountInactiveException() {
        super("Conta desativada");
    }
}
