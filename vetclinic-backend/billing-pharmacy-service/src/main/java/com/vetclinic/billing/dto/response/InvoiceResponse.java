package com.vetclinic.billing.dto.response;

import com.vetclinic.billing.constant.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Bảng kê viện phí: dịch vụ kỹ thuật và thuốc tách riêng (BR-21). visitCode / petName / ownerId null nếu clinical tạm không phản hồi. */
public record InvoiceResponse(
        Long id,
        String invoiceCode,
        Long visitId,
        String visitCode,
        String petName,
        Long ownerId,
        Long prescriptionId,
        Long cashierId,
        BigDecimal totalServiceAmount,
        BigDecimal totalMedicineAmount,
        BigDecimal totalVatAmount,
        BigDecimal discountAmount,
        BigDecimal finalAmount,
        PaymentStatus paymentStatus,
        Instant paidAt,
        String notes,
        Instant createdAt,
        List<InvoiceItemResponse> items
) {
}
