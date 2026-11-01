package com.vetclinic.clinical.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/** Đổi giá không ảnh hưởng các chỉ định đã tạo: đơn giá đã được snapshot (BR-25). Ngừng dùng bằng active=false, không xóa. */
public record UpdateServiceRequest(
        @Size(min = 1, max = 150, message = "Tên từ 1 đến 150 ký tự") String name,
        @DecimalMin(value = "0.00", message = "Đơn giá không được âm") @Digits(integer = 10, fraction = 2, message = "Đơn giá tối đa 10 chữ số nguyên và 2 chữ số thập phân") BigDecimal price,
        String description,
        Boolean active
) {
}
