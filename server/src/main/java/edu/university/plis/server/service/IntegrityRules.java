package edu.university.plis.server.service;

import java.time.Duration;
import java.util.List;

public final class IntegrityRules {
    public static final int DISCONNECTION_THRESHOLD = 3;
    public static final int FOCUS_LOSS_THRESHOLD = 5;
    public static final double SIMILARITY_THRESHOLD = 0.90;

    private IntegrityRules() {
    }

    public static boolean isFastCompletionOutlier(Duration candidate, List<Duration> peerDurations) {
        if (candidate == null || peerDurations == null || peerDurations.size() < 2) {
            return false;
        }
        double averagePeerSeconds = peerDurations.stream()
                .mapToLong(Duration::toSeconds)
                .average()
                .orElse(Double.MAX_VALUE);
        return candidate.toSeconds() >= 0 && candidate.toSeconds() < averagePeerSeconds * 0.5;
    }
}
