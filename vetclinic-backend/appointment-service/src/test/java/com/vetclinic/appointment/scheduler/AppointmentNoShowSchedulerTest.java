package com.vetclinic.appointment.scheduler;

import com.vetclinic.appointment.config.BookingProperties;
import com.vetclinic.appointment.constant.AppointmentStatus;
import com.vetclinic.appointment.repository.AppointmentRepository;
import com.vetclinic.appointment.service.AppointmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentNoShowSchedulerTest {

    // 2026-10-30 09:00 giờ Việt Nam, thời gian chờ 30 phút -> ngưỡng cắt 08:30
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-30T02:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private AppointmentService appointmentService;

    private AppointmentNoShowScheduler scheduler() {
        BookingProperties properties = new BookingProperties("Asia/Ho_Chi_Minh", 30,
                List.of("08:00", "08:30"), 30, 2, 60, false, 5);
        return new AppointmentNoShowScheduler(appointmentRepository, appointmentService, properties, CLOCK);
    }

    @Test
    void scan_queriesConfirmedAppointmentsEndedBeforeGraceCutoff() {
        when(appointmentRepository.findIdsEndedBefore(AppointmentStatus.CONFIRMED,
                LocalDate.of(2026, 10, 30), LocalTime.of(8, 30))).thenReturn(List.of());

        scheduler().scanAndMarkNoShowAppointments();

        verify(appointmentService, never()).markNoShowBySystem(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void scan_oneFailingAppointmentDoesNotStopTheRest() {
        when(appointmentRepository.findIdsEndedBefore(AppointmentStatus.CONFIRMED,
                LocalDate.of(2026, 10, 30), LocalTime.of(8, 30))).thenReturn(List.of(1L, 2L, 3L));
        when(appointmentService.markNoShowBySystem(1L)).thenReturn(true);
        when(appointmentService.markNoShowBySystem(2L)).thenThrow(new IllegalStateException("lỗi giả lập"));
        when(appointmentService.markNoShowBySystem(3L)).thenReturn(true);

        scheduler().scanAndMarkNoShowAppointments();

        verify(appointmentService).markNoShowBySystem(1L);
        verify(appointmentService).markNoShowBySystem(2L);
        verify(appointmentService).markNoShowBySystem(3L);
    }
}
