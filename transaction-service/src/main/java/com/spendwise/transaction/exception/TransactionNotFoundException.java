package com.spendwise.transaction.exception;

import java.util.UUID;

public class TransactionNotFoundException extends RuntimeException {
    public TransactionNotFoundException(UUID id) {
        super("Transação não encontrada: " + id);
    }
}
