package com.vetclinic.auth.dto.request;

import com.vetclinic.auth.constant.UserStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeStatusRequest(
        @NotNull(message = "Trạng thái không được để trống")
        UserStatus status
) {
}
