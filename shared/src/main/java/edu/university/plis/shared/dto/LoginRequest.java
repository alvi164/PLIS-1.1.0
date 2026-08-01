package edu.university.plis.shared.dto;

public record LoginRequest(String username, String password, String deviceId, String deviceName) {
    public LoginRequest(String username, String password) {
        this(username, password, null, null);
    }
}
