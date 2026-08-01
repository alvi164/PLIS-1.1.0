package edu.university.plis.server.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IntegrityRulesTest {
    @Test
    void flagsCompletionBelowHalfThePeerAverage() {
        boolean outlier = IntegrityRules.isFastCompletionOutlier(
                Duration.ofMinutes(12), List.of(Duration.ofMinutes(40), Duration.ofMinutes(50)));

        assertThat(outlier).isTrue();
    }

    @Test
    void requiresTwoPeersAndARealOutlier() {
        assertThat(IntegrityRules.isFastCompletionOutlier(
                Duration.ofMinutes(12), List.of(Duration.ofMinutes(40)))).isFalse();
        assertThat(IntegrityRules.isFastCompletionOutlier(
                Duration.ofMinutes(30), List.of(Duration.ofMinutes(40), Duration.ofMinutes(50)))).isFalse();
    }
}
