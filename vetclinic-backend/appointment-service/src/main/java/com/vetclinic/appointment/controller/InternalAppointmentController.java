package com.vetclinic.appointment.controller;

import com.vetclinic.appointment.dto.response.AppointmentResponse;
import com.vetclinic.appointment.service.AppointmentService;
import com.vetclinic.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cho clinical-service tra cứu lịch hẹn trước khi tiếp nhận (check-in) khách có hẹn: lịch phải đang CONFIRMED.
 * Nằm dưới /internal/**: Gateway không định tuyến, chỉ truy cập được trong mạng Docker.
 */
@RestController
@RequestMapping("/internal/appointments")
@RequiredArgsConstructor
public class InternalAppointmentController {

    private final AppointmentService appointmentService;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AppointmentResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(appointmentService.getForInternal(id)));
    }
}
