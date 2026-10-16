package com.relay.exception;

public class RelayException extends RuntimeException {
    public RelayException(String message) {
        super(message);
    }

    public RelayException(String message, Throwable cause) {
        super(message, cause);
    }
}
