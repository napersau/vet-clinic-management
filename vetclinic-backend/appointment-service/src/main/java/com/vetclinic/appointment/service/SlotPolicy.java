package com.vetclinic.appointment.service;

import com.vetclinic.appointment.client.AuthClient;
import com.vetclinic.appointment.config.BookingProperties;
import com.vetclinic.common.dto.ApiResponse;
import com.vetclinic.common.exception.AppException;
import com.vetclinic.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Quy tắc về khung giờ: hợp lệ hay không, đã qua chưa, và sức chứa.
 * Sức chứa mỗi khung = số Bác sĩ ACTIVE, lấy qua OpenFeign từ auth-service tại thời điểm cần (không lưu cố định).
 */
@Component
@RequiredArgsConstructor
public class SlotPolicy {

    private final BookingProperties properties;
    private final Clock clock;
    private final AuthClient authClient;

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public LocalTime endOf(LocalTime start) {
        return start.plusMinutes(properties.slotMinutes());
    }

    /** Giờ bắt đầu phải đúng một trong các khung chuẩn. */
    public LocalTime requireSlot(LocalTime start) {
        if (!properties.slots().contains(start)) {
            throw new AppException(ErrorCode.INVALID_APPOINTMENT_SLOT,
                    "Khung giờ phải là một trong các khung 30 phút trong ngày làm việc (08:00-11:30, 13:30-17:00)");
        }
        return start;
    }

    /** Ngày phải từ hôm nay đến hết thời hạn đặt trước. */
    public void assertDateInRange(LocalDate date) {
        LocalDate today = today();
        if (date.isBefore(today)) {
            throw new AppException(ErrorCode.INVALID_INPUT_DATA, "Không thể chọn ngày trong quá khứ");
        }
        if (date.isAfter(today.plusDays(properties.maxAdvanceDays()))) {
            throw new AppException(ErrorCode.INVALID_INPUT_DATA,
                    "Chỉ được đặt lịch trước tối đa " + properties.maxAdvanceDays() + " ngày");
        }
    }

    /** Khung giờ của ngày hôm nay đã bắt đầu thì không đặt được nữa. */
    public boolean isPast(LocalDate date, LocalTime start) {
        LocalDateTime now = now();
        return date.isBefore(now.toLocalDate())
                || (date.isEqual(now.toLocalDate()) && !start.isAfter(now.toLocalTime()));
    }

    /** Kiểm tra đủ điều kiện để đặt vào (ngày, khung giờ) này. */
    public void assertBookable(LocalDate date, LocalTime start) {
        requireSlot(start);
        assertDateInRange(date);
        if (isPast(date, start)) {
            throw new AppException(ErrorCode.INVALID_INPUT_DATA, "Khung giờ này đã qua, vui lòng chọn khung giờ khác");
        }
    }

    /**
     * Sức chứa tối đa của một khung giờ = COUNT(bác sĩ ACTIVE). Bằng 0 nghĩa là chưa có bác sĩ nào hoạt động,
     * mọi khung giờ đều kín.
     */
    public int activeDoctorCapacity() {
        ApiResponse<Long> response = authClient.getActiveDoctorCount();
        if (response == null || response.getData() == null) {
            throw new AppException(ErrorCode.SERVICE_UNAVAILABLE);
        }
        return (int) Math.min(Math.max(response.getData(), 0L), Integer.MAX_VALUE);
    }
}
