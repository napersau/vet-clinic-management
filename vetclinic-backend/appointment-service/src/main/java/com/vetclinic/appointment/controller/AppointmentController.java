package com.vetclinic.appointment.controller;

import com.vetclinic.appointment.constant.AppointmentStatus;
import com.vetclinic.appointment.dto.request.CancelAppointmentRequest;
import com.vetclinic.appointment.dto.request.CreateAppointmentRequest;
import com.vetclinic.appointment.dto.request.RescheduleAppointmentRequest;
import com.vetclinic.appointment.dto.response.AppointmentHistoryResponse;
import com.vetclinic.appointment.dto.response.AppointmentResponse;
import com.vetclinic.appointment.dto.response.SlotAvailabilityResponse;
import com.vetclinic.appointment.service.AppointmentFilter;
import com.vetclinic.appointment.service.AppointmentService;
import com.vetclinic.appointment.service.SlotService;
import com.vetclinic.common.dto.ApiResponse;
import com.vetclinic.common.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final SlotService slotService;
    private final com.vetclinic.appointment.service.AppointmentEnricher enricher;
    private final com.vetclinic.appointment.service.AppointmentReportService reportService;

    /** Tình trạng 14 khung giờ của một ngày (Còn N chỗ / Đã kín lịch). */
    @GetMapping("/slots")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<SlotAvailabilityResponse>>> slots(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(slotService.getSlots(date)));
    }

    /** UC-14 (chủ nuôi -> PENDING) và UC-16 (lễ tân -> CONFIRMED). */
    @PostMapping
    @PreAuthorize("hasAnyRole('PET_OWNER','RECEPTIONIST')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> create(@Valid @RequestBody CreateAppointmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Đặt lịch hẹn thành công", appointmentService.create(request)));
    }

    /**
     * Chủ nuôi: chỉ thấy lịch của mình. Lễ tân / Admin: lọc theo ngày, trạng thái, mã, chủ nuôi, thú cưng.
     * Hàng chờ duyệt (UC-17): status=PENDING&amp;sortBy=createdAt&amp;ascending=true.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('PET_OWNER','RECEPTIONIST','ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<AppointmentResponse>>> search(
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) Long petId,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "false") boolean ascending,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        AppointmentFilter filter = new AppointmentFilter(ownerId, petId, code, fromDate, toDate, status,
                sortBy, ascending, page, size);
        var result = appointmentService.search(filter);
        // Tên thú cưng / chủ nuôi lấy theo lô từ clinical và auth, ngoài transaction
        result.setItems(enricher.enrichAll(result.getItems()));
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PET_OWNER','RECEPTIONIST','ADMIN')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(enricher.enrich(appointmentService.getById(id))));
    }

    /** Audit Trail vòng đời lịch hẹn. */
    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','ADMIN')")
    public ResponseEntity<ApiResponse<List<AppointmentHistoryResponse>>> history(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(appointmentService.getHistory(id)));
    }

    /** UC-17: duyệt lịch hẹn trực tuyến. */
    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> confirm(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Đã xác nhận lịch hẹn", appointmentService.confirm(id)));
    }

    /** UC-15 (chủ nuôi tự hủy) và UC-17 (lễ tân từ chối / hủy). Bắt buộc có lý do. */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('PET_OWNER','RECEPTIONIST')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> cancel(@PathVariable Long id,
                                                                   @Valid @RequestBody CancelAppointmentRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đã hủy lịch hẹn", appointmentService.cancel(id, request)));
    }

    /** UC-15: đổi ngày / khung giờ. */
    @PostMapping("/{id}/reschedule")
    @PreAuthorize("hasAnyRole('PET_OWNER','RECEPTIONIST')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> reschedule(@PathVariable Long id,
                                                                       @Valid @RequestBody RescheduleAppointmentRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đổi lịch hẹn thành công", appointmentService.reschedule(id, request)));
    }

    /** EX-01: lễ tân xác nhận khách vắng mặt (ngoài tiến trình quét tự động). */
    @PostMapping("/{id}/no-show")
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> noShow(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Đã đánh dấu khách vắng mặt", appointmentService.markNoShow(id)));
    }

    /** FR-70: thống kê lịch hẹn theo trạng thái và kênh đặt, tỉ lệ bỏ hẹn (NO_SHOW) và tỉ lệ hủy. */
    @GetMapping("/reports/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<com.vetclinic.appointment.dto.response.AppointmentReportResponse>> report(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(ApiResponse.success(reportService.summary(fromDate, toDate)));
    }
}
