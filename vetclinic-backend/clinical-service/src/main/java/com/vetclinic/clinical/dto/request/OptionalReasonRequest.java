package com.vetclinic.clinical.dto.request;

import jakarta.validation.constraints.Size;

public record OptionalReasonRequest(
        @Size(max = 500, message = "Lý do tối đa 500 ký tự") String reason
) {
}
