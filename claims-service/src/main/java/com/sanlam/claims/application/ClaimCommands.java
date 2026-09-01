package com.sanlam.claims.application;

import com.sanlam.claims.domain.ClaimType;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class ClaimCommands
{

    private ClaimCommands()
    {
    }

    public record SubmitClaim(String idempotencyKey, String externalReference, ClaimType claimType, String clientId,
            String policyNumber, LocalDate incidentDate, BigDecimal amount, String currency)
    {
    }
}
