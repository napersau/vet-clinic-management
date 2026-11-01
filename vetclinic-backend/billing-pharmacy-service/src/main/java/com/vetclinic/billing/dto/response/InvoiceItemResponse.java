package com.vetclinic.billing.dto.response;

import com.vetclinic.billing.constant.InvoiceItemType;

import java.math.BigDecimal;

public record InvoiceItemResponse(
        Long id,
        InvoiceItemType itemType,
        Long referenceId,
        String itemName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal vatRate,
        BigDecimal vatAmount,
        BigDecimal subtotal
) {
}
