package com.sethu.claims.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "claim")
public class Claim {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String externalReference;

    @Column(nullable = false)
    private String clientId;

    @Column(nullable = false)
    private String policyNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimType claimType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimPriority priority;

    @Column(nullable = false)
    private LocalDate incidentDate;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column
    private String paymentReference;

    @Version
    private long version;

    protected Claim() {
    }

    public Claim(
            UUID id,
            String externalReference,
            String clientId,
            String policyNumber,
            ClaimType claimType,
            ClaimStatus status,
            ClaimPriority priority,
            LocalDate incidentDate,
            BigDecimal amount,
            String currency,
            Instant createdAt
    ) {
        this.id = id;
        this.externalReference = externalReference;
        this.clientId = clientId;
        this.policyNumber = policyNumber;
        this.claimType = claimType;
        this.status = status;
        this.priority = priority;
        this.incidentDate = incidentDate;
        this.amount = amount;
        this.currency = currency;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static Claim receive(
            UUID id,
            String externalReference,
            String clientId,
            String policyNumber,
            ClaimType claimType,
            LocalDate incidentDate,
            BigDecimal amount,
            String currency,
            Instant receivedAt
    ) {
        ClaimPriority priority = switch (claimType) {
            case DEATH -> ClaimPriority.CRITICAL;
            case DISABILITY -> ClaimPriority.HIGH;
            case STANDARD -> ClaimPriority.NORMAL;
        };

        return new Claim(
                id,
                externalReference,
                clientId,
                policyNumber,
                claimType,
                ClaimStatus.RECEIVED,
                priority,
                incidentDate,
                amount,
                currency,
                receivedAt
        );
    }

    public void moveTo(ClaimStatus nextStatus, Instant timestamp) {
        validateTransition(nextStatus);
        status = nextStatus;
        updatedAt = timestamp;
    }

    public void markPaymentPending(String paymentReference, Instant timestamp) {
        if (status != ClaimStatus.APPROVED) {
            throw new IllegalStateException("Only approved claims can enter PAYMENT_PENDING");
        }
        this.paymentReference = paymentReference;
        this.status = ClaimStatus.PAYMENT_PENDING;
        this.updatedAt = timestamp;
    }

    public void markPaid(String receivedPaymentReference, Instant timestamp) {
        if (status == ClaimStatus.PAID && receivedPaymentReference.equals(paymentReference)) {
            return;
        }
        if (status != ClaimStatus.PAYMENT_PENDING) {
            throw new IllegalStateException("Claim is not waiting for payment");
        }
        this.paymentReference = receivedPaymentReference;
        this.status = ClaimStatus.PAID;
        this.updatedAt = timestamp;
    }

    private void validateTransition(ClaimStatus nextStatus) {
        if (status == ClaimStatus.RECEIVED && nextStatus == ClaimStatus.VALIDATING) {
            return;
        }
        if (status == ClaimStatus.VALIDATING
                && (nextStatus == ClaimStatus.MANUAL_REVIEW
                || nextStatus == ClaimStatus.APPROVED
                || nextStatus == ClaimStatus.REJECTED)) {
            return;
        }
        if (status == ClaimStatus.MANUAL_REVIEW
                && (nextStatus == ClaimStatus.APPROVED || nextStatus == ClaimStatus.REJECTED)) {
            return;
        }
        if (status == ClaimStatus.PAYMENT_PENDING
                && (nextStatus == ClaimStatus.PAID || nextStatus == ClaimStatus.PAYMENT_FAILED)) {
            return;
        }
        if (status == ClaimStatus.APPROVED && nextStatus == ClaimStatus.PAYMENT_PENDING) {
            return;
        }
        if (status == ClaimStatus.PAYMENT_FAILED && nextStatus == ClaimStatus.PAYMENT_PENDING) {
            return;
        }

        throw new IllegalStateException(
                "Invalid claim transition from " + status + " to " + nextStatus
        );
    }

    public UUID getId() { return id; }
    public String getExternalReference() { return externalReference; }
    public String getClientId() { return clientId; }
    public String getPolicyNumber() { return policyNumber; }
    public ClaimType getClaimType() { return claimType; }
    public ClaimStatus getStatus() { return status; }
    public ClaimPriority getPriority() { return priority; }
    public LocalDate getIncidentDate() { return incidentDate; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getPaymentReference() { return paymentReference; }
}
