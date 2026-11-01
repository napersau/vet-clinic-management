package com.vetclinic.clinical.dto.request;

import com.vetclinic.clinical.constant.ServiceCategory;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CreateServiceRequest(
        @NotBlank(message = "Mã dịch vụ không được để trống") @Size(max = 50, message = "Mã tối đa 50 ký tự") String code,
        @NotBlank(message = "Tên dịch vụ không được để trống") @Size(max = 150, message = "Tên tối đa 150 ký tự") String name,
        @NotNull(message = "Nhóm dịch vụ không được để trống") ServiceCategory category,
        @NotNull(message = "Đơn giá không được để trống") @DecimalMin(value = "0.00", message = "Đơn giá không được âm") @Digits(integer = 10, fraction = 2, message = "Đơn giá tối đa 10 chữ số nguyên và 2 chữ số thập phân") BigDecimal price,
        String description
) {
}
