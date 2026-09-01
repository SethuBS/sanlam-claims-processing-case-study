package com.sethu.claims.application;

import com.sethu.claims.domain.Claim;
import com.sethu.claims.domain.ClaimStatus;
import com.sethu.claims.integration.PaymentSystemClient;
import com.sethu.claims.repository.ClaimRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

@Component
@ConditionalOnProperty(
        name = "payment.reconciliation.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class PaymentReconciler {

    private final ClaimRepository claimRepository;
    private final PaymentSystemClient paymentSystemClient;
    private final Clock clock;
    private final Duration minimumAge;

    public PaymentReconciler(
            ClaimRepository claimRepository,
            PaymentSystemClient paymentSystemClient,
            Clock clock,
            @Value("${payment.reconciliation.minimum-age:5s}") Duration minimumAge
    ) {
        this.claimRepository = claimRepository;
        this.paymentSystemClient = paymentSystemClient;
        this.clock = clock;
        this.minimumAge = minimumAge;
    }

    @Scheduled(fixedDelayString = "${payment.reconciliation.poll-delay-ms:5000}")
    public void reconcile() {
        var candidates = claimRepository.findTop20ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                ClaimStatus.PAYMENT_PENDING,
                clock.instant().minus(minimumAge)
        );

        for (Claim claim : candidates) {
            try {
                PaymentSystemClient.PaymentStatus payment = paymentSystemClient.getPayment(claim.getId());
                if (payment == null) {
                    continue;
                }
                if ("COMPLETED".equals(payment.status())) {
                    claim.markPaid(payment.paymentReference(), clock.instant());
                } else if ("FAILED".equals(payment.status())) {
                    claim.markPaymentFailed(clock.instant());
                }
                claimRepository.save(claim);
            } catch (RuntimeException ignored) {
                // A failed lookup stays pending and will be retried on the next reconciliation pass.
            }
        }
    }
}
