package com.vetclinic.auth.dto.request;

import com.vetclinic.common.security.RoleCode;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(
        @NotNull(message = "Vai trò không được để trống")
        RoleCode roleCode
) {
}
