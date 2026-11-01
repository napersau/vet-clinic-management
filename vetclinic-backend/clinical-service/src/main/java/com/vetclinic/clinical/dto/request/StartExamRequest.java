package com.vetclinic.clinical.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/** Bác sĩ trực tiếp cân và đo thân nhiệt trong phòng khám (không phải Lễ tân). */
public record StartExamRequest(
        @NotNull(message = "Cân nặng thú cưng không được để trống") @DecimalMin(value = "0.1", message = "Cân nặng phải lớn hơn 0.1 kg") @DecimalMax(value = "150.0", message = "Cân nặng không hợp lý (tối đa 150 kg)") BigDecimal weightAtVisit,
        @NotNull(message = "Thân nhiệt thú cưng không được để trống") @DecimalMin(value = "35.0", message = "Thân nhiệt đo được quá thấp (tối thiểu 35°C)") @DecimalMax(value = "43.0", message = "Thân nhiệt đo được quá cao (tối đa 43°C)") BigDecimal temperature,
        @Size(max = 1000, message = "Ghi chú triệu chứng lâm sàng không được vượt quá 1000 ký tự") String clinicalNotes
) {
}
