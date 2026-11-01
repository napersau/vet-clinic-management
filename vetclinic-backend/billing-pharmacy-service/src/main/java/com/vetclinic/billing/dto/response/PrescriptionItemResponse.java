package com.vetclinic.billing.dto.response;

import java.math.BigDecimal;

public record PrescriptionItemResponse(
        Long id,
        Long medicineId,
        String medicineCode,
        String medicineName,
        String activeIngredient,
        String unit,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal,
        String dosage,
        String frequency,
        int durationDays,
        String instructions
) {
}
