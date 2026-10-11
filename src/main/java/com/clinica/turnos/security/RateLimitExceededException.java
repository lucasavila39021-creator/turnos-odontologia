package com.clinica.turnos.security;

/**
 * Thrown when rate limit exceeded — mapped to 429.
 */
public class RateLimitExceededException extends RuntimeException {
    private final int retryAfterSeconds;

    public RateLimitExceededException(String message, int retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
