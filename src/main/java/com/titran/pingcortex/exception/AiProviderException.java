package com.titran.pingcortex.exception;

public class AiProviderException extends RuntimeException {
    private final boolean retryable;
    private final long retryAfterMillis;

    public AiProviderException(String message, Throwable cause) {
        this(message, cause, false, 0);
    }

    public AiProviderException(String message, Throwable cause, boolean retryable, long retryAfterMillis) {
        super(message, cause);
        this.retryable = retryable;
        this.retryAfterMillis = retryAfterMillis;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public long retryAfterMillis() {
        return retryAfterMillis;
    }
}
