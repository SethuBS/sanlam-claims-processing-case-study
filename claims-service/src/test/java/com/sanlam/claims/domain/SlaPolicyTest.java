package com.sanlam.claims.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlaPolicyTest
{

    @Test
    void calculatesDueTimeFromConfigurablePriorityRules()
    {
        SlaPolicy policy = new SlaPolicy(Map.of(ClaimPriority.CRITICAL, Duration.ofHours(2), ClaimPriority.HIGH,
                Duration.ofHours(8), ClaimPriority.NORMAL, Duration.ofDays(2)));
        Instant receivedAt = Instant.parse("2026-08-31T08:00:00Z");

        assertThat(policy.dueAt(ClaimPriority.CRITICAL, receivedAt)).isEqualTo(Instant.parse("2026-08-31T10:00:00Z"));
        assertThat(policy.dueAt(ClaimPriority.HIGH, receivedAt)).isEqualTo(Instant.parse("2026-08-31T16:00:00Z"));
        assertThat(policy.dueAt(ClaimPriority.NORMAL, receivedAt)).isEqualTo(Instant.parse("2026-09-02T08:00:00Z"));
    }

    @Test
    void requiresAPositiveRuleForEveryPriority()
    {
        assertThatThrownBy(() -> new SlaPolicy(
                Map.of(ClaimPriority.CRITICAL, Duration.ofHours(2), ClaimPriority.HIGH, Duration.ofHours(8))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("NORMAL");
    }
}
