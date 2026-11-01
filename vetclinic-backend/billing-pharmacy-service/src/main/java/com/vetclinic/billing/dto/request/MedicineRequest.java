package com.vetclinic.billing.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Thêm thuốc vào danh mục (Admin). initialStock và minStockAlert bỏ trống được. */
public record MedicineRequest(
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 150) String activeIngredient,
        @NotBlank @Size(max = 50) String dosageForm,
        @NotBlank @Size(max = 30) String unit,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal salePrice,
        @Min(0) @Max(1_000_000) Integer initialStock,
        @Min(0) @Max(1_000_000) Integer minStockAlert
) {
}
