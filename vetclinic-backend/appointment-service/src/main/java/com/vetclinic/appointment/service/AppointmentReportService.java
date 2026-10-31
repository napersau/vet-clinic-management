package com.vetclinic.appointment.service;

import com.vetclinic.appointment.dto.response.AppointmentReportResponse;
import com.vetclinic.appointment.repository.AppointmentRepository;
import com.vetclinic.appointment.constant.AppointmentStatus;
import com.vetclinic.appointment.constant.BookingChannel;
import com.vetclinic.common.exception.AppException;
import com.vetclinic.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AppointmentReportService {

    private static final int DEFAULT_DAYS = 30;
    private static final long MAX_DAYS = 366;

    private final AppointmentRepository appointmentRepository;
    private final Clock clock;

    /** FR-70: thống kê lịch hẹn có ngày hẹn trong [fromDate, toDate] (mặc định 30 ngày gần nhất, tối đa 366 ngày). */
    @Transactional(readOnly = true)
    public AppointmentReportResponse summary(LocalDate fromDate, LocalDate toDate) {
        LocalDate to = toDate != null ? toDate : LocalDate.now(clock);
        LocalDate from = fromDate != null ? fromDate : to.minusDays(DEFAULT_DAYS - 1L);
        if (from.isAfter(to)) {
            throw new AppException(ErrorCode.INVALID_INPUT_DATA, "Ngày bắt đầu phải trước hoặc bằng ngày kết thúc");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw new AppException(ErrorCode.INVALID_INPUT_DATA, "Khoảng báo cáo tối đa " + MAX_DAYS + " ngày");
        }
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (AppointmentStatus s : AppointmentStatus.values()) {
            byStatus.put(s.name(), 0L);
        }
        for (Object[] row : appointmentRepository.countByStatusBetween(from, to)) {
            byStatus.put(((AppointmentStatus) row[0]).name(), (Long) row[1]);
        }
        Map<String, Long> byChannel = new LinkedHashMap<>();
        for (BookingChannel c : BookingChannel.values()) {
            byChannel.put(c.name(), 0L);
        }
        for (Object[] row : appointmentRepository.countByChannelBetween(from, to)) {
            byChannel.put(((BookingChannel) row[0]).name(), (Long) row[1]);
        }
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long noShow = byStatus.get("NO_SHOW");
        long outcomes = noShow + byStatus.get("CHECKED_IN");
        return new AppointmentReportResponse(from, to, total, byStatus, byChannel,
                ratio(noShow, outcomes), ratio(byStatus.get("CANCELLED"), total));
    }

    private static double ratio(long part, long whole) {
        return whole == 0 ? 0.0 : Math.round(part * 10000.0 / whole) / 10000.0;
    }
}
