package com.vetclinic.clinical.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

/** Có dịch vụ LABORATORY / IMAGING thì ca chuyển sang WAITING_RESULTS và giải phóng Bác sĩ; chỉ có dịch vụ tại phòng khám thì giữ IN_PROGRESS. */
public record OrderServicesRequest(
        @NotEmpty(message = "Phải chọn ít nhất một dịch vụ") @Size(max = 20, message = "Tối đa 20 dịch vụ mỗi lần") List<@Valid ServiceOrderItem> services
) {
}
