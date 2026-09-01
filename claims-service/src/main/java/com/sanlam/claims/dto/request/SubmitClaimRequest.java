package com.sanlam.claims.dto.request;

import com.sanlam.claims.domain.ClaimType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record SubmitClaimRequest(@NotBlank String externalReference, @NotNull ClaimType claimType,
        @NotBlank String clientId, @NotBlank String policyNumber, @NotNull LocalDate incidentDate,
        @NotNull MoneyRequest amount)
{
}
