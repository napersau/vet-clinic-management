package com.vetclinic.auth.dto.response;

import com.vetclinic.auth.constant.UserStatus;
import com.vetclinic.common.security.RoleCode;

/**
 * Thông tin tối thiểu cho các service khác (hiển thị tên bác sĩ, chủ nuôi, số phòng...).
 */
public record UserSummaryResponse(
        Long id,
        String fullName,
        String phoneNumber,
        String email,
        String roomNumber,
        RoleCode role,
        UserStatus status
) {
}
