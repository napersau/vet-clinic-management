package com.vetclinic.clinical.controller;

import com.vetclinic.clinical.dto.response.DoctorStatusResponse;
import com.vetclinic.clinical.service.DoctorStatusService;
import com.vetclinic.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Danh sách ca của bác sĩ đang đăng nhập (GET /me/visits) thêm ở NV2. */
@RestController
@RequestMapping("/api/v1/clinical/doctors")
@RequiredArgsConstructor
public class DoctorController {

    private final DoctorStatusService doctorStatusService;

    /** Bảng điều phối của Lễ tân: trạng thái BUSY / AVAILABLE / AVAILABLE_PRIORITY_CALLBACK của từng Bác sĩ. */
    @GetMapping("/status")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','ADMIN')")
    public ResponseEntity<ApiResponse<List<DoctorStatusResponse>>> board() {
        return ResponseEntity.ok(ApiResponse.success(doctorStatusService.board()));
    }
}
