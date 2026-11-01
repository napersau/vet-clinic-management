package com.vetclinic.billing.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Lưu đơn thuốc điện tử (UC-31). visitId bắt buộc khi tạo (POST) và bị bỏ qua khi cập nhật (PUT).
 * Cập nhật = thay toàn bộ danh sách thuốc.
 */
public record PrescriptionRequest(
        Long visitId,
        @Size(max = 4000) String doctorNotes,
        @NotEmpty @Size(max = 20) List<@Valid PrescriptionItemRequest> items
) {
}
