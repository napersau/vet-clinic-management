package com.vetclinic.clinical.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Danh sách chỉ số mới của một dịch vụ xét nghiệm (thay toàn bộ). Rỗng = quay về "gồm mọi chỉ số". */
public record ServiceLabItemsRequest(
        @NotNull @Size(max = 100) List<Long> labTestItemIds
) {
}
