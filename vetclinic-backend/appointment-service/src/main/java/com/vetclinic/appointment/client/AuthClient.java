package com.vetclinic.appointment.client;

import com.vetclinic.appointment.dto.response.UserSummaryResponse;
import com.vetclinic.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Gọi endpoint nội bộ của auth-service (không cần header người dùng, Gateway không định tuyến ra ngoài).
 */
@FeignClient(name = "auth-service", path = "/internal/auth")
public interface AuthClient {

    /** MAX_APPOINTMENTS_PER_SLOT = số Bác sĩ (VETERINARIAN) đang ACTIVE. */
    @GetMapping("/doctors/active-count")
    ApiResponse<Long> getActiveDoctorCount();

    @GetMapping("/users/{id}")
    ApiResponse<UserSummaryResponse> getUser(@PathVariable("id") Long id);

    /** Tra cứu hàng loạt theo ?ids=1,2,3 để làm giàu danh sách lịch hẹn. */
    @GetMapping("/users")
    ApiResponse<java.util.List<UserSummaryResponse>> getUsers(@org.springframework.web.bind.annotation.RequestParam("ids") java.util.List<Long> ids);
}
