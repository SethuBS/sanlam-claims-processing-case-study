package com.sanlam.claims.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record PaymentStatusEventRequest(@NotNull UUID eventId, @NotNull UUID claimId, @NotBlank String paymentReference,
        @NotBlank String status, @NotNull Instant occurredAt)
{
}
