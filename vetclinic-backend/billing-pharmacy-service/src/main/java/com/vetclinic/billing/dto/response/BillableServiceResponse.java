package com.vetclinic.billing.dto.response;

import java.math.BigDecimal;

/** Dòng dịch vụ đã hoàn thành của một đợt khám, do clinical-service trả về (category giữ dạng chuỗi để không phụ thuộc enum bên kia). */
public record BillableServiceResponse(
        Long serviceId,
        String code,
        String name,
        String category,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
) {
}
