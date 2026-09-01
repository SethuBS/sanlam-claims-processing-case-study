package com.sanlam.claims.dto.response;

public record PolicyEligibilityResponse(String validationReference, boolean eligible, boolean manualReviewRequired,
        String ruleVersion, String[] reasonCodes)
{
}
