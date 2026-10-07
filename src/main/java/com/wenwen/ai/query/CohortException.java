package com.wenwen.ai.query;

public final class CohortException extends RuntimeException {
    private final int status;
    private final String code;
    public CohortException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
    public int getStatus() { return status; }
    public String getCode() { return code; }
}
