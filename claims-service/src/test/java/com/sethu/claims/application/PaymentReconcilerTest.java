package com.sethu.claims.application;

import com.sethu.claims.domain.Claim;
import com.sethu.claims.domain.ClaimStatus;
import com.sethu.claims.domain.ClaimType;
import com.sethu.claims.integration.PaymentSystemClient;
import com.sethu.claims.repository.ClaimRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentReconcilerTest {

    private static final Instant NOW = Instant.parse("2026-09-01T08:00:00Z");
    @Mock ClaimRepository claimRepository;
    @Mock PaymentSystemClient paymentSystemClient;
    private PaymentReconciler reconciler;

    @BeforeEach
    void setUp() {
        reconciler = new PaymentReconciler(
                claimRepository,
                paymentSystemClient,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofSeconds(5)
        );
    }

    @Test
    void recoversACompletedPaymentWhenTheCallbackWasLost() {
        Claim claim = pendingClaim();
        when(claimRepository.findTop20ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                ClaimStatus.PAYMENT_PENDING, NOW.minusSeconds(5)
        )).thenReturn(List.of(claim));
        when(paymentSystemClient.getPayment(claim.getId()))
                .thenReturn(new PaymentSystemClient.PaymentStatus("PAY-RECOVERED", "COMPLETED"));

        reconciler.reconcile();

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.PAID);
        verify(claimRepository).save(claim);
    }

    @Test
    void downstreamOutageLeavesTheClaimPendingForTheNextPass() {
        Claim claim = pendingClaim();
        when(claimRepository.findTop20ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(any(), any()))
                .thenReturn(List.of(claim));
        when(paymentSystemClient.getPayment(claim.getId()))
                .thenThrow(new IllegalStateException("temporary outage"));

        reconciler.reconcile();

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.PAYMENT_PENDING);
    }

    private Claim pendingClaim() {
        Claim claim = Claim.receive(
                UUID.randomUUID(), "WEB-RECON", "CLIENT-1", "POL-1", ClaimType.STANDARD,
                LocalDate.of(2026, 8, 31), BigDecimal.TEN, "ZAR", NOW.minusSeconds(30)
        );
        claim.moveTo(ClaimStatus.VALIDATING, NOW.minusSeconds(29));
        claim.moveTo(ClaimStatus.APPROVED, NOW.minusSeconds(28));
        claim.markPaymentPending("PAY-RECOVERED", NOW.minusSeconds(20));
        return claim;
    }
}
