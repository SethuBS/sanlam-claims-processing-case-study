package com.sanlam.mock.policy.dto.request;

import java.math.BigDecimal;

public record PolicyEligibilityRequest(String policyNumber, BigDecimal amount)
{
}
