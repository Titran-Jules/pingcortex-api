package com.titran.pingcortex.exception;

public class AnalysisNotRetryableException extends RuntimeException {
    public AnalysisNotRetryableException() {
        super("Course analysis can only be retried when its status is FAILED.");
    }
}
