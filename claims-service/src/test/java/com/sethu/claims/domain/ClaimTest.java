package com.sethu.claims.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClaimTest {

    @Test
    void deathClaimIsCritical() {
        Claim claim = Claim.receive(
                UUID.randomUUID(),
                "WEB-000123",
                "CLIENT-10542",
                "POL-847563",
                ClaimType.DEATH,
                LocalDate.of(2026, 8, 31),
                new BigDecimal("250000.00"),
                "ZAR",
                Instant.parse("2026-08-31T08:15:31Z")
        );

        assertThat(claim.getPriority()).isEqualTo(ClaimPriority.CRITICAL);
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.RECEIVED);
    }

    @Test
    void invalidTransitionIsRejected() {
        Claim claim = Claim.receive(
                UUID.randomUUID(),
                "WEB-000123",
                "CLIENT-10542",
                "POL-847563",
                ClaimType.STANDARD,
                LocalDate.of(2026, 8, 31),
                new BigDecimal("1000.00"),
                "ZAR",
                Instant.now()
        );

        assertThatThrownBy(() -> claim.moveTo(ClaimStatus.PAID, Instant.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid claim transition");
    }

    @Test
    void duplicatePaymentCallbackIsSafe() {
        UUID claimId = UUID.randomUUID();
        Claim claim = Claim.receive(
                claimId,
                "WEB-000123",
                "CLIENT-10542",
                "POL-847563",
                ClaimType.STANDARD,
                LocalDate.of(2026, 8, 31),
                new BigDecimal("1000.00"),
                "ZAR",
                Instant.now()
        );

        claim.moveTo(ClaimStatus.VALIDATING, Instant.now());
        claim.moveTo(ClaimStatus.APPROVED, Instant.now());
        claim.markPaymentPending("PAY-001", Instant.now());
        claim.markPaid("PAY-001", Instant.now());

        claim.markPaid("PAY-001", Instant.now());

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.PAID);
        assertThat(claim.getPaymentReference()).isEqualTo("PAY-001");
    }
}
