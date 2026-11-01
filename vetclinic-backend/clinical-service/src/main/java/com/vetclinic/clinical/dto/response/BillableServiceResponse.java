package com.vetclinic.clinical.dto.response;

import com.vetclinic.clinical.constant.ServiceCategory;
import java.math.BigDecimal;

/** Cho billing-pharmacy-service lập hóa đơn: chỉ gồm dịch vụ COMPLETED, dịch vụ CANCELLED không bao giờ tính tiền. */
public record BillableServiceResponse(
        Long serviceId,
        String code,
        String name,
        ServiceCategory category,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
) {
}
