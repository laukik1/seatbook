package com.seatbook.exception;

public class ApiException extends RuntimeException {
    public final int status;
    public final String reason;

    public ApiException(int status, String reason, String message) {
        super(message, null, false, false); // no stack trace: declines are normal, cheap outcomes
        this.status = status;
        this.reason = reason;
    }
}
