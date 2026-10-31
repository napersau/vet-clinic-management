package com.vetclinic.appointment.scheduler;

import com.vetclinic.appointment.config.BookingProperties;
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
 * EX-01 / FR-17: tự động chuyển NO_SHOW các lịch hẹn CONFIRMED mà khách không đến, để trả suất cho khách vãng lai.
 * Quá giờ KẾT THÚC khung hẹn + noShowGraceMinutes mà chưa check-in là vắng mặt.
 *
 * Khác code mẫu trong quy chuẩn (§11.3): quét theo (ngày, giờ) thay vì chỉ "hôm nay" nên lịch của các ngày trước còn sót
 * (service tắt qua đêm / cuối tuần) cũng được xử lý; mỗi lịch hẹn một transaction riêng nên một lỗi không kéo cả mẻ
 * rollback; có múi giờ rõ ràng nên chạy đúng giờ Việt Nam dù container ở UTC.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentNoShowScheduler {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentService appointmentService;
    private final BookingProperties properties;
    private final Clock clock;

    @Scheduled(cron = "${vetclinic.booking.no-show-cron}", zone = "${vetclinic.booking.zone}")
    public void scanAndMarkNoShowAppointments() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusMinutes(properties.noShowGraceMinutes());
        List<Long> ids = appointmentRepository.findIdsEndedBefore(
                AppointmentStatus.CONFIRMED, cutoff.toLocalDate(), cutoff.toLocalTime());
        if (ids.isEmpty()) {
            return;
        }

        int marked = 0;
        for (Long id : ids) {
            try {
                if (appointmentService.markNoShowBySystem(id)) {
                    marked++;
                }
            } catch (RuntimeException e) {
                log.error("Không thể chuyển NO_SHOW cho lịch hẹn id={}: {}", id, e.getMessage(), e);
            }
        }
        log.info("Quét NO_SHOW: {} ứng viên, đã chuyển {} lịch hẹn", ids.size(), marked);
    }
}
