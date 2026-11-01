package com.vetclinic.clinical.event;

import com.vetclinic.clinical.config.ClinicalRabbitConfig;
import com.vetclinic.clinical.service.VisitService;
import com.vetclinic.common.event.PaymentAndDispenseCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * billing-pharmacy-service thu tiền và xuất thuốc trong transaction của nó rồi phát PaymentAndDispenseCompletedEvent;
 * clinical-service đóng đợt khám (WAITING_PAYMENT -> COMPLETED) tại đây, không ghi chéo sang DB của service khác.
 * Idempotent: nhận trùng sự kiện thì không có thêm tác dụng. Lỗi thì retry 3 lần rồi vào DLQ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCompletedListener {

    private final VisitService visitService;

    @RabbitListener(queues = ClinicalRabbitConfig.PAYMENT_COMPLETED_QUEUE)
    public void onPaymentCompleted(PaymentAndDispenseCompletedEvent event) {
        log.info("Nhận PaymentAndDispenseCompletedEvent: visitId={}", event.visitId());
        visitService.completeAfterPayment(event.visitId(), event.actorUserId());
    }
}
