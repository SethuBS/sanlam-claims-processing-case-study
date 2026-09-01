package com.sanlam.claims.dto.response;

public record ClientValidationResponse(String validationReference, boolean valid, String clientStatus,
        String[] reasonCodes)
{
}
