package com.sanlam.claims.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record MoneyRequest(@NotBlank String currency, @NotNull @Positive BigDecimal value)
{
}
