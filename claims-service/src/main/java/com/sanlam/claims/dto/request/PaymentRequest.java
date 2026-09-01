package com.sanlam.claims.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentRequest(UUID claimId, BigDecimal amount, String currency)
{
}
