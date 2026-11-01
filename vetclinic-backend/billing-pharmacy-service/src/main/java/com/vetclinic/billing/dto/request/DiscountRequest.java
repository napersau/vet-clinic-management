package com.vetclinic.billing.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** UC-34: số tiền chiết khấu (0 = bỏ chiết khấu). Không được vượt tổng viện phí (EX-14). */
public record DiscountRequest(
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal discountAmount,
        @Size(max = 1000) String notes
) {
}
