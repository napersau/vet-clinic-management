package com.vetclinic.billing.dto.response;

import java.math.BigDecimal;

public record MedicineResponse(
        Long id,
        String code,
        String name,
        String activeIngredient,
        String dosageForm,
        String unit,
        BigDecimal salePrice,
        int stockQuantity,
        int minStockAlert,
        boolean lowStock,
        boolean active
) {
}
