package com.sanlam.claims.dto.request;

import java.math.BigDecimal;

public record PolicyEligibilityRequest(String policyNumber, BigDecimal amount)
{
}
