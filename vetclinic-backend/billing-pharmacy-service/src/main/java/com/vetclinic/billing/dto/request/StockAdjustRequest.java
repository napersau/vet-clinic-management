package com.vetclinic.billing.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** UC-37: cân chỉnh sau kiểm kê. delta dương = thừa, âm = hao hụt / hư hỏng / hết hạn; khác 0; bắt buộc ghi lý do. */
public record StockAdjustRequest(
        @NotNull @Min(-1_000_000) @Max(1_000_000) Integer delta,
        @NotBlank @Size(max = 255) String reason
) {
}
