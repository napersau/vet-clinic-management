package com.vetclinic.common.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Cầu nối "sự kiện nội bộ -> RabbitMQ" chỉ chạy SAU KHI transaction commit.
 * Service nghiệp vụ KHÔNG gọi rabbitTemplate trực tiếp; chỉ cần:
 * <pre>
 * eventPublisher.publishEvent(new VisitClosedToPaymentEvent(...)); // trong method @Transactional
 * </pre>
 * Nếu transaction rollback, sự kiện bị bỏ. Nếu không có transaction (fallbackExecution) thì gửi ngay.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnClass(name = "org.springframework.amqp.rabbit.core.RabbitTemplate")
public class DomainEventRabbitPublisher {

    private final RabbitTemplate rabbitTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void publish(DomainEvent event) {
        rabbitTemplate.convertAndSend(EventRouting.EXCHANGE, event.routingKey(), event);
        log.info("Published {} [{}] eventId={}", event.getClass().getSimpleName(), event.routingKey(), event.eventId());
    }
}
