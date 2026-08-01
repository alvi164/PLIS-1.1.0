package edu.university.plis.server.service;

public class AccessPendingException extends RuntimeException {
    public AccessPendingException(String message) {
        super(message);
    }
}
