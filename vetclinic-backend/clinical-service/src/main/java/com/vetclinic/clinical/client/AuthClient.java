package com.vetclinic.clinical.client;

import com.vetclinic.clinical.dto.response.UserSummaryResponse;
import com.vetclinic.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/** Endpoint nội bộ của auth-service (không cần header người dùng, Gateway không định tuyến ra ngoài). */
@FeignClient(name = "auth-service", path = "/internal/auth")
public interface AuthClient {

    @GetMapping("/users/{id}")
    ApiResponse<UserSummaryResponse> getUser(@PathVariable("id") Long id);

    /** Tra cứu hàng loạt theo ?ids=1,2,3 (hiển thị tên bác sĩ trong bệnh án). */
    @GetMapping("/users")
    ApiResponse<List<UserSummaryResponse>> getUsers(@org.springframework.web.bind.annotation.RequestParam("ids") List<Long> ids);

    /** Bác sĩ (VETERINARIAN) đang ACTIVE kèm số phòng cố định. */
    @GetMapping("/doctors")
    ApiResponse<List<UserSummaryResponse>> getActiveDoctors();
}
