package com.vetclinic.clinical.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record LabResultItemRequest(
        @NotNull(message = "Chỉ số xét nghiệm không được để trống") Long labTestItemId,
        @NotNull(message = "Giá trị đo được không được để trống") @DecimalMin(value = "0.00", message = "Giá trị đo không được âm") @Digits(integer = 6, fraction = 2, message = "Giá trị đo tối đa 6 chữ số nguyên và 2 chữ số thập phân") BigDecimal value,
        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự") String notes
) {
}
