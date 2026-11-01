package com.vetclinic.clinical.dto.request;

import jakarta.validation.constraints.*;

public record ServiceOrderItem(
        @NotNull(message = "Dịch vụ không được để trống") Long serviceId,
        @Min(value = 1, message = "Số lượng tối thiểu là 1") @Max(value = 20, message = "Số lượng tối đa là 20") Integer quantity,
        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự") String notes
) {
}
