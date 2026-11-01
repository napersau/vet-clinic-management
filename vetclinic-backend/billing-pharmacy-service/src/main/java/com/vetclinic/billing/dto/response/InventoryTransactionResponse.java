package com.vetclinic.billing.dto.response;

import com.vetclinic.billing.constant.InventoryTransactionType;

import java.time.Instant;

public record InventoryTransactionResponse(
        Long id,
        Long medicineId,
        InventoryTransactionType transactionType,
        int quantity,
        int balanceAfter,
        Long referenceId,
        String notes,
        Long createdBy,
        Instant createdAt
) {
}
