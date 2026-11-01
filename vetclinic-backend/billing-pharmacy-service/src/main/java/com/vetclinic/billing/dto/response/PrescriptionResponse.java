package com.vetclinic.billing.dto.response;

import com.vetclinic.billing.constant.PaymentStatus;
import com.vetclinic.billing.constant.PrescriptionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * visitCode / petName lấy từ clinical-service, null nếu service đó tạm không phản hồi.
 * invoiceId / invoiceStatus cho màn hình phát thuốc biết hóa đơn đã PAID chưa.
 */
public record PrescriptionResponse(
        Long id,
        Long visitId,
        String visitCode,
        String petName,
        PrescriptionStatus status,
        String doctorNotes,
        BigDecimal totalAmount,
        Long invoiceId,
        PaymentStatus invoiceStatus,
        Long dispensedBy,
        Instant dispensedAt,
        Instant createdAt,
        List<PrescriptionItemResponse> items
) {
}
