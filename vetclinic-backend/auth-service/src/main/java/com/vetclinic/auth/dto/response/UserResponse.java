package com.vetclinic.auth.dto.response;

import com.vetclinic.auth.constant.UserStatus;
import com.vetclinic.common.security.RoleCode;

import java.time.Instant;

public record UserResponse(
        Long id,
        String username,
        String fullName,
        String phoneNumber,
        String email,
        String address,
        String roomNumber,
        RoleCode role,
        UserStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
