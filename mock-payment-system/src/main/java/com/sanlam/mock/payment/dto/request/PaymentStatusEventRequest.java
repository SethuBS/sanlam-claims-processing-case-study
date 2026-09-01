package com.sanlam.mock.payment.dto.request;

import java.time.Instant;
import java.util.UUID;

public record PaymentStatusEventRequest(UUID eventId, UUID claimId, String paymentReference, String status,
        Instant occurredAt)
{
}
