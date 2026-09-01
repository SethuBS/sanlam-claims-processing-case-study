package com.sanlam.claims.dto.response;

import com.sanlam.claims.domain.Claim;

import java.util.UUID;

public record ClaimResponse(UUID claimId, String externalReference, String status, String priority,
        String paymentReference)
{
    public static ClaimResponse from(Claim claim)
    {
        return new ClaimResponse(claim.getId(), claim.getExternalReference(), claim.getStatus().name(),
                claim.getPriority().name(), claim.getPaymentReference());
    }
}
