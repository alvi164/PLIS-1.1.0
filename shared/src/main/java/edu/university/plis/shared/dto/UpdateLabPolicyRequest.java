package edu.university.plis.shared.dto;

import edu.university.plis.shared.model.NetworkConnectionType;

public record UpdateLabPolicyRequest(int maxParticipants, NetworkConnectionType connectionPolicy) {
}
