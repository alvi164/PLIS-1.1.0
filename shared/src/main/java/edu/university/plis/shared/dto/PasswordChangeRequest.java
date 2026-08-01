package edu.university.plis.shared.dto;

public record PasswordChangeRequest(String currentPassword, String newPassword) {
}
