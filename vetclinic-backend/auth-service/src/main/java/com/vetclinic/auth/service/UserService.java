package com.vetclinic.auth.service;

import com.vetclinic.auth.constant.UserStatus;
import com.vetclinic.auth.dto.request.ChangePasswordRequest;
import com.vetclinic.auth.dto.request.RegisterRequest;
import com.vetclinic.auth.dto.request.CreateUserRequest;
import com.vetclinic.auth.dto.request.UpdateProfileRequest;
import com.vetclinic.auth.dto.request.UpdateUserRequest;
import com.vetclinic.auth.dto.response.UserResponse;
import com.vetclinic.auth.dto.response.UserSummaryResponse;
import com.vetclinic.common.dto.PageResponse;
import com.vetclinic.common.security.RoleCode;

import java.util.List;

public interface UserService {

    // --- Người dùng hiện tại ---
    UserResponse getMe();

    UserResponse updateMe(UpdateProfileRequest request);

    /** Đổi mật khẩu và thu hồi mọi Refresh Token (đăng xuất khỏi tất cả thiết bị). */
    void changePassword(ChangePasswordRequest request);

    /** Chủ nuôi tự đăng ký (public): luôn là PET_OWNER, ACTIVE, tên đăng nhập là số điện thoại. */
    UserResponse register(RegisterRequest request);

    // --- Quản lý tài khoản (ADMIN, RECEPTIONIST có giới hạn) ---
    UserResponse create(CreateUserRequest request);

    PageResponse<UserResponse> search(RoleCode role, UserStatus status, String keyword, int page, int size);

    UserResponse getById(Long id);

    UserResponse update(Long id, UpdateUserRequest request);

    /**
     * Admin đổi vai trò giữa các vai trò nhân sự (không tự đổi vai trò của mình, không đổi từ/sang PET_OWNER). Thu hồi mọi
     * Refresh Token để phiên sau mang vai trò mới; số phòng bị xóa nếu vai trò mới không có phòng cố định.
     */
    UserResponse changeRole(Long id, RoleCode role);

    /**
     * Đặt lại mật khẩu (quên mật khẩu): Admin cho mọi tài khoản trừ chính mình, Lễ tân chỉ cho chủ nuôi. Thu hồi mọi Refresh
     * Token. Người dùng tự đổi mật khẩu của mình bằng change-password.
     */
    void resetPassword(Long id, String newPassword);

    /** Khóa / ngừng hoạt động sẽ thu hồi mọi Refresh Token của tài khoản. */
    UserResponse changeStatus(Long id, UserStatus status);

    List<UserSummaryResponse> getActiveDoctors();

    // --- Dùng cho các service khác qua /internal ---
    UserSummaryResponse getSummary(Long id);

    List<UserSummaryResponse> getSummaries(List<Long> ids);

    long countActiveDoctors();
}
