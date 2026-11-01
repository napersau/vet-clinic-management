package com.vetclinic.billing.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** UC-37: nhập thêm thuốc vào kho (tăng tồn kho). */
public record StockImportRequest(
        @NotNull @Min(1) @Max(1_000_000) Integer quantity,
        @Size(max = 255) String notes
) {
}
