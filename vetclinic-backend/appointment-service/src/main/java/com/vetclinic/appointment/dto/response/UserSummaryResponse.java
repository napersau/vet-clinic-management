package com.vetclinic.appointment.dto.response;

import com.vetclinic.common.security.RoleCode;

/**
 * Phần auth-service trả về mà appointment-service cần. Các trường thừa trong JSON được bỏ qua.
 */
public record UserSummaryResponse(Long id, String fullName, String phoneNumber, RoleCode role, String status) {
}
