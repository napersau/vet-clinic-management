package com.vetclinic.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * billing-pharmacy-service -> notification-service: tồn kho chạm ngưỡng min_stock_alert, cảnh báo Admin.
 */
public record LowStockEvent(
        UUID eventId,
        Instant occurredAt,
        Long actorUserId,
        Long medicineId,
        String medicineCode,
        String medicineName,
        int stockQuantity,
        int minStockAlert
) implements DomainEvent {

    @Override
    public String routingKey() {
        return EventRouting.STOCK_LOW_ALERT;
    }
}
