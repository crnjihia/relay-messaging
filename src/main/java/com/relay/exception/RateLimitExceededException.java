package com.relay.exception;

public class RateLimitExceededException extends RelayException {
    private final String limitType;
    private final long retryAfterSeconds;

    public RateLimitExceededException(String message, String limitType, long retryAfterSeconds) {
        super(message);
        this.limitType = limitType;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String getLimitType() {
        return limitType;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
