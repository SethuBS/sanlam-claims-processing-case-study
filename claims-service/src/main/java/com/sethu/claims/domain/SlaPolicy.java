package com.sethu.claims.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class SlaPolicy {

    private final Map<ClaimPriority, Duration> targets;

    public SlaPolicy(Map<ClaimPriority, Duration> targets) {
        Objects.requireNonNull(targets, "targets must not be null");
        EnumMap<ClaimPriority, Duration> validated = new EnumMap<>(ClaimPriority.class);
        for (ClaimPriority priority : ClaimPriority.values()) {
            Duration duration = targets.get(priority);
            if (duration == null || duration.isZero() || duration.isNegative()) {
                throw new IllegalArgumentException("A positive SLA target is required for " + priority);
            }
            validated.put(priority, duration);
        }
        this.targets = Map.copyOf(validated);
    }

    public Instant dueAt(ClaimPriority priority, Instant receivedAt) {
        Objects.requireNonNull(priority, "priority must not be null");
        Objects.requireNonNull(receivedAt, "receivedAt must not be null");
        return receivedAt.plus(targets.get(priority));
    }
}
