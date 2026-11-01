package com.vetclinic.clinical.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Đơn thuốc của một đợt khám do billing-pharmacy-service trả về (chỉ phần cần cho bệnh án và hướng dẫn dùng thuốc). */
public record PrescriptionSummary(
        Long id,
        Long visitId,
        String status,
        String doctorNotes,
        BigDecimal totalAmount,
        Instant dispensedAt,
        List<Item> items
) {

    public record Item(
            Long medicineId,
            String medicineCode,
            String medicineName,
            String activeIngredient,
            String unit,
            int quantity,
            String dosage,
            String frequency,
            int durationDays,
            String instructions
    ) {
    }
}
