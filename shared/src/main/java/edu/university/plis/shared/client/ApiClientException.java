package edu.university.plis.shared.client;

public final class ApiClientException extends RuntimeException {
    private final int statusCode;

    public ApiClientException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public ApiClientException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
    }

    public int statusCode() {
        return statusCode;
    }
}
