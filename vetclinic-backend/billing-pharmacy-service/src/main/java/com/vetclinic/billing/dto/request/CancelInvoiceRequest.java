package com.vetclinic.billing.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelInvoiceRequest(
        @NotBlank(message = "Lý do hủy hóa đơn không được để trống")
        @Size(max = 500, message = "Lý do tối đa 500 ký tự")
        String reason
) {
}
