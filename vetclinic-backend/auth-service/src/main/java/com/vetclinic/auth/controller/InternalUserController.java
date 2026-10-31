package com.vetclinic.auth.controller;

import com.vetclinic.auth.dto.response.UserSummaryResponse;
import com.vetclinic.auth.service.UserService;
import com.vetclinic.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoint cho các service khác gọi qua OpenFeign (appointment đếm bác sĩ để tính sức chứa slot,
 * clinical lấy tên bác sĩ / chủ nuôi...). Nằm dưới /internal/**: CommonSecurityConfig cho phép không cần header
 * người dùng (consumer RabbitMQ, scheduler cũng gọi được) và Gateway không định tuyến path này ra ngoài,
 * nên chỉ truy cập được từ bên trong mạng Docker. Không bao giờ publish port của service ra host.
 */
@RestController
@RequestMapping("/internal/auth")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserService userService;

    /** MAX_APPOINTMENTS_PER_SLOT = số bác sĩ ACTIVE (TAI_LIEU_MO_TA_NGHIEP_VU §5.3). */
    @GetMapping("/doctors/active-count")
    public ResponseEntity<ApiResponse<Long>> activeDoctorCount() {
        return ResponseEntity.ok(ApiResponse.success(userService.countActiveDoctors()));
    }

    @GetMapping("/doctors")
    public ResponseEntity<ApiResponse<List<UserSummaryResponse>>> activeDoctors() {
        return ResponseEntity.ok(ApiResponse.success(userService.getActiveDoctors()));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<ApiResponse<UserSummaryResponse>> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(userService.getSummary(id)));
    }

    /** Tra cứu hàng loạt theo ?ids=1,2,3 để tránh N+1 lời gọi Feign khi hiển thị danh sách. */
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<UserSummaryResponse>>> getUsers(@RequestParam List<Long> ids) {
        return ResponseEntity.ok(ApiResponse.success(userService.getSummaries(ids)));
    }
}
