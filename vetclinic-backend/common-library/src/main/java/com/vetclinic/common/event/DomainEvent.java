package com.vetclinic.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Mọi sự kiện liên service đều mang eventId (để consumer chống xử lý trùng),
 * thời điểm phát sinh và người thao tác (ghi vào bảng *_status_histories ở phía consumer).
 * actorUserId = null nghĩa là hệ thống tự động (scheduler).
 */
public interface DomainEvent {

    UUID eventId();

    Instant occurredAt();

    Long actorUserId();

    String routingKey();
}
