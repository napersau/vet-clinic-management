package com.vetclinic.appointment.dto.request;

import com.vetclinic.appointment.constant.BookingChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Không có trường bác sĩ: khách hàng tuyệt đối không chọn bác sĩ khi đặt lịch (BR-04).
 * <ul>
 *   <li>PET_OWNER (UC-14): ownerId và bookingChannel bị bỏ qua; luôn là chính người đăng nhập, kênh ONLINE, trạng thái PENDING.</li>
 *   <li>RECEPTIONIST (UC-16): bắt buộc ownerId; bookingChannel là PHONE hoặc WALK_IN; trạng thái CONFIRMED ngay.</li>
 * </ul>
 */
public record CreateAppointmentRequest(
        @NotNull(message = "Thú cưng không được để trống")
        Long petId,

        Long ownerId,

        @NotNull(message = "Ngày hẹn không được để trống")
        LocalDate appointmentDate,

        @NotNull(message = "Khung giờ không được để trống")
        LocalTime startTime,

        @NotBlank(message = "Lý do khám / triệu chứng không được để trống")
        @Size(max = 1000, message = "Lý do khám tối đa 1000 ký tự")
        String reason,

        BookingChannel bookingChannel
) {
}
