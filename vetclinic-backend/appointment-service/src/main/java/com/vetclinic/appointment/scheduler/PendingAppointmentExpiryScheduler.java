package com.vetclinic.appointment.scheduler;

import com.vetclinic.appointment.constant.AppointmentStatus;
import com.vetclinic.appointment.repository.AppointmentRepository;
import com.vetclinic.appointment.service.AppointmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Lịch đặt online (PENDING) mà đến hết khung giờ hẹn vẫn chưa được lễ tân duyệt thì tự hủy, nếu không nó chiếm sức chứa
 * của khung giờ đó mãi mãi (PENDING tính vào sức chứa). Cùng nguyên tắc với AppointmentNoShowScheduler: quét theo (ngày, giờ)
 * để bù các ngày trước, mỗi lịch một transaction, có múi giờ rõ ràng.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingAppointmentExpiryScheduler {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentService appointmentService;
    private final Clock clock;

    @Scheduled(cron = "${vetclinic.booking.pending-expiry-cron}", zone = "${vetclinic.booking.zone}")
    public void expireStalePendingAppointments() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> ids = appointmentRepository.findIdsEndedBefore(AppointmentStatus.PENDING, now.toLocalDate(), now.toLocalTime());
        if (ids.isEmpty()) {
            return;
        }
        int expired = 0;
        for (Long id : ids) {
            try {
                if (appointmentService.expirePendingBySystem(id)) {
                    expired++;
                }
            } catch (RuntimeException e) {
                log.error("Không thể hủy lịch hẹn PENDING quá hạn id={}: {}", id, e.getMessage(), e);
            }
        }
        log.info("Quét PENDING quá hạn: {} ứng viên, đã hủy {} lịch hẹn", ids.size(), expired);
    }
}
