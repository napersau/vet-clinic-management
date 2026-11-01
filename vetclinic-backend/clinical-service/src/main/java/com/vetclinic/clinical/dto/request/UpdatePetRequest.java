package com.vetclinic.clinical.dto.request;

import com.vetclinic.clinical.constant.PetGender;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Trường nào null thì giữ nguyên. Không đổi được loài (loài quyết định khoảng tham chiếu xét nghiệm). */
public record UpdatePetRequest(
        @Size(min = 1, max = 100, message = "Tên từ 1 đến 100 ký tự") String name,
        @Size(max = 100, message = "Giống tối đa 100 ký tự") String breed,
        PetGender gender,
        @PastOrPresent(message = "Ngày sinh không được ở tương lai") LocalDate dateOfBirth,
        @Size(max = 100, message = "Màu lông tối đa 100 ký tự") String color,
        @DecimalMin(value = "0.01", message = "Cân nặng phải lớn hơn 0") @DecimalMax(value = "150.0", message = "Cân nặng tối đa 150 kg") BigDecimal weight,
        Boolean neutered,
        @Size(max = 1000, message = "Đặc điểm nhận dạng tối đa 1000 ký tự") String identifyingCharacteristics,
        @Size(max = 2000, message = "Ghi chú tối đa 2000 ký tự") String notes
) {
}
