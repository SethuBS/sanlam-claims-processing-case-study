package com.sanlam.mock.payment.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentRequest(UUID claimId, BigDecimal amount, String currency)
{
}
