package com.vetclinic.clinical.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ReferenceRangeRequest(
        @NotNull @DecimalMin("0.00") @Digits(integer = 6, fraction = 2) BigDecimal minValue,
        @NotNull @DecimalMin("0.00") @Digits(integer = 6, fraction = 2) BigDecimal maxValue
) {
}
