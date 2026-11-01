package com.vetclinic.billing.dto.response;

import com.vetclinic.billing.constant.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record InvoiceSummaryResponse(
        Long id,
        String invoiceCode,
        Long visitId,
        String visitCode,
        String petName,
        Long prescriptionId,
        BigDecimal finalAmount,
        PaymentStatus paymentStatus,
        Instant paidAt,
        Instant createdAt
) {
}
