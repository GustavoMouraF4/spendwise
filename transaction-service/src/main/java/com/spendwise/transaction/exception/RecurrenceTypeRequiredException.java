package com.spendwise.transaction.exception;

public class RecurrenceTypeRequiredException extends RuntimeException {
    public RecurrenceTypeRequiredException() {
        super("recurrenceType é obrigatório quando recurring=true");
    }
}
