package com.uncomplex.exception;

public class OperationBusyException extends RuntimeException {
    public OperationBusyException() {
        super("A matching operation is still running. Please retry shortly.");
    }
}
