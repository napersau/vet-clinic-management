package com.vetclinic.clinical.dto.request;

import jakarta.validation.constraints.*;

public record ReasonRequest(
        @NotBlank(message = "Lý do không được để trống") @Size(max = 500, message = "Lý do tối đa 500 ký tự") String reason
) {
}
