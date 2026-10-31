package com.vetclinic.appointment.event;

import com.vetclinic.appointment.service.AppointmentService;
import com.vetclinic.common.event.VisitCheckedInEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * clinical-service tạo đợt khám trong transaction của nó rồi phát VisitCheckedInEvent; appointment-service chuyển lịch
 * hẹn sang CHECKED_IN tại đây (không ghi chéo sang DB của service khác, CLAUDE.md §5.2).
 * Idempotent: nhận trùng sự kiện thì không có thêm tác dụng. Lỗi thì retry 3 lần rồi vào DLQ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VisitCheckedInListener {

    private final AppointmentService appointmentService;

    @RabbitListener(queues = AppointmentRabbitConfig.VISIT_CHECKED_IN_QUEUE)
    public void onVisitCheckedIn(VisitCheckedInEvent event) {
        if (event.appointmentId() == null) {
            // Khách vãng lai (walk-in): không có lịch hẹn nào để cập nhật
            return;
        }
        log.info("Nhận VisitCheckedInEvent: visitCode={}, appointmentId={}", event.visitCode(), event.appointmentId());
        appointmentService.markCheckedIn(event.appointmentId(), event.actorUserId(), event.visitCode());
    }
}
