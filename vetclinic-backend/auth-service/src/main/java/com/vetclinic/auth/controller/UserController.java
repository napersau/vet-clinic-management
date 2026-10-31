package com.vetclinic.auth.controller;

import com.vetclinic.auth.constant.UserStatus;
import com.vetclinic.auth.dto.request.ChangeRoleRequest;
import com.vetclinic.auth.dto.request.ChangeStatusRequest;
import com.vetclinic.auth.dto.request.ResetPasswordRequest;
import com.vetclinic.auth.dto.request.CreateUserRequest;
import com.vetclinic.auth.dto.request.UpdateUserRequest;
import com.vetclinic.auth.dto.response.UserResponse;
import com.vetclinic.auth.dto.response.UserSummaryResponse;
import com.vetclinic.auth.service.UserService;
import com.vetclinic.common.dto.ApiResponse;
import com.vetclinic.common.dto.PageResponse;
import com.vetclinic.common.security.RoleCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Quản lý tài khoản (FR-01, FR-02, UC-01, UC-02, UC-18).
 */
@RestController
@RequestMapping("/api/v1/auth/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** ADMIN xem tất cả; RECEPTIONIST bị ép lọc PET_OWNER để tra cứu khách hàng theo SĐT / họ tên. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST')")
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> search(
            @RequestParam(required = false) RoleCode role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(userService.search(role, status, keyword, page, size)));
    }

    /** Danh sách Bác sĩ đang hoạt động kèm số phòng (màn hình điều phối của Lễ tân). */
    @GetMapping("/doctors")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST')")
    public ResponseEntity<ApiResponse<List<UserSummaryResponse>>> activeDoctors() {
        return ResponseEntity.ok(ApiResponse.success(userService.getActiveDoctors()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST','VETERINARIAN','LAB_TECHNICIAN','IMAGING_TECHNICIAN')")
    public ResponseEntity<ApiResponse<UserResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(userService.getById(id)));
    }

    /** ADMIN tạo mọi vai trò; RECEPTIONIST chỉ tạo PET_OWNER (kiểm tra trong service). */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST')")
    public ResponseEntity<ApiResponse<UserResponse>> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Tạo tài khoản thành công", userService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> update(@PathVariable Long id,
                                                            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Cập nhật tài khoản thành công", userService.update(id, request)));
    }

    /** Đổi vai trò nhân sự (FR-01). Thu hồi phiên đăng nhập của tài khoản đó. */
    @PostMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> changeRole(@PathVariable Long id,
                                                                @Valid @RequestBody ChangeRoleRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đổi vai trò thành công", userService.changeRole(id, request.roleCode())));
    }

    /** Quên mật khẩu: Admin đặt lại cho mọi tài khoản, Lễ tân chỉ cho chủ nuôi (kiểm tra trong service). */
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST')")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@PathVariable Long id,
                                                           @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(id, request.newPassword());
        return ResponseEntity.ok(ApiResponse.<Void>success("Đã đặt lại mật khẩu", null));
    }

    /** Khóa / ngừng / kích hoạt tài khoản. Chuyển trạng thái dùng POST theo quy chuẩn Action Endpoint. */
    @PostMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> changeStatus(@PathVariable Long id,
                                                                  @Valid @RequestBody ChangeStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đổi trạng thái tài khoản thành công",
                userService.changeStatus(id, request.status())));
    }
}
