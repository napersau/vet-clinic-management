package com.vetclinic.billing.event;

import com.vetclinic.billing.config.BillingRabbitConfig;
import com.vetclinic.billing.service.InvoiceService;
import com.vetclinic.common.event.VisitClosedToPaymentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * clinical-service chốt ca (hoặc đóng ca khách bỏ về) rồi phát VisitClosedToPaymentEvent; billing lập hóa đơn PENDING
 * trong DB của mình, không ghi chéo sang clinical. Idempotent (visit_id UNIQUE). Lỗi thì retry 3 lần rồi vào DLQ;
 * khi đó Lễ tân vẫn lập tay được bằng POST /api/v1/billing/invoices/from-visit/{visitId}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VisitClosedListener {

    private final InvoiceService invoiceService;

    @RabbitListener(queues = BillingRabbitConfig.VISIT_CLOSED_QUEUE)
    public void onVisitClosed(VisitClosedToPaymentEvent event) {
        log.info("Nhận VisitClosedToPaymentEvent: visitId={}, abandoned={}", event.visitId(), event.abandoned());
        invoiceService.createForVisit(event.visitId(), event.occurredAt());
    }
}
