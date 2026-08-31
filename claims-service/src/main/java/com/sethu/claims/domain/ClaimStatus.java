package com.sethu.claims.domain;

public enum ClaimStatus {
    RECEIVED,
    VALIDATING,
    MANUAL_REVIEW,
    APPROVED,
    REJECTED,
    PAYMENT_PENDING,
    PAYMENT_FAILED,
    PAID
}
