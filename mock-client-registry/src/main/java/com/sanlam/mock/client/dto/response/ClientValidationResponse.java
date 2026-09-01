package com.sanlam.mock.client.dto.response;

public record ClientValidationResponse(String validationReference, boolean valid, String clientStatus,
        String[] reasonCodes)
{
}
