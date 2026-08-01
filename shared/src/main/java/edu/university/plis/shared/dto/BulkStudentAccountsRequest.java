package edu.university.plis.shared.dto;

import java.util.List;

public record BulkStudentAccountsRequest(List<CreateStudentAccountRequest> accounts) {
}
