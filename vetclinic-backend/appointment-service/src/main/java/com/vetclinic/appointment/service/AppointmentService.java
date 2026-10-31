package com.vetclinic.appointment.service;

import com.vetclinic.appointment.dto.request.CancelAppointmentRequest;
import com.vetclinic.appointment.dto.request.CreateAppointmentRequest;
import com.vetclinic.appointment.dto.request.RescheduleAppointmentRequest;
import com.vetclinic.appointment.dto.response.AppointmentHistoryResponse;
import com.vetclinic.appointment.dto.response.AppointmentResponse;
import com.vetclinic.common.dto.PageResponse;

import java.util.List;

public interface AppointmentService {

    /** UC-14 (chủ nuôi, trạng thái PENDING) và UC-16 (lễ tân, CONFIRMED ngay). */
    AppointmentResponse create(CreateAppointmentRequest request);

    PageResponse<AppointmentResponse> search(AppointmentFilter filter);

    AppointmentResponse getById(Long id);

    List<AppointmentHistoryResponse> getHistory(Long id);

    /** UC-17: PENDING -> CONFIRMED (lễ tân). */
    AppointmentResponse confirm(Long id);

    /** UC-15 / UC-17: hủy (chủ nuôi tự hủy) hoặc từ chối (lễ tân). PENDING / CONFIRMED -> CANCELLED. */
    AppointmentResponse cancel(Long id, CancelAppointmentRequest request);

    /** UC-15: đổi ngày / khung giờ, giữ nguyên trạng thái. */
    AppointmentResponse reschedule(Long id, RescheduleAppointmentRequest request);

    /** EX-01: lễ tân xác nhận vắng mặt. CONFIRMED -> NO_SHOW, chỉ khi đã quá giờ hẹn + thời gian chờ. */
    AppointmentResponse markNoShow(Long id);

    // ------------------------------------------------------------------ không cần người dùng đăng nhập

    /** Tiến trình quét tự động. Trả về true nếu đã chuyển NO_SHOW. Idempotent. */
    boolean markNoShowBySystem(Long id);

    /**
     * Tiến trình quét tự động: lịch PENDING đã quá giờ KẾT THÚC khung hẹn mà chưa ai duyệt thì hủy để trả suất. Trả về true
     * nếu đã hủy. Idempotent.
     */
    boolean expirePendingBySystem(Long id);

    /** Consumer của VisitCheckedInEvent. CONFIRMED -> CHECKED_IN. Idempotent. */
    void markCheckedIn(Long appointmentId, Long actorUserId, String visitCode);

    /** Cho clinical-service tra cứu lịch hẹn trước khi check-in (không kiểm tra chủ sở hữu). */
    AppointmentResponse getForInternal(Long id);
}
