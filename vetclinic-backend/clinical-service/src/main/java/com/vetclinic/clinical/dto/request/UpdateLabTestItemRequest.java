package com.vetclinic.clinical.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Sửa chỉ số xét nghiệm. Mã chỉ số bất biến vì kết quả cũ tham chiếu tới chỉ số này. */
public record UpdateLabTestItemRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 30) String unit,
        @Size(max = 255) String description
) {
}
