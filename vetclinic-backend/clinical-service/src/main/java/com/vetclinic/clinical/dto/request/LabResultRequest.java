package com.vetclinic.clinical.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record LabResultRequest(
        @NotEmpty(message = "Phải nhập ít nhất một chỉ số") @Size(max = 50, message = "Tối đa 50 chỉ số mỗi lần") List<@Valid LabResultItemRequest> items
) {
}
