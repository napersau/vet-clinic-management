package com.vetclinic.clinical.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SpeciesRequest(
        @NotBlank(message = "Mã loài không được để trống") @Size(max = 50, message = "Mã loài tối đa 50 ký tự") String code,
        @NotBlank(message = "Tên loài không được để trống") @Size(max = 100, message = "Tên loài tối đa 100 ký tự") String name,
        @Size(max = 255, message = "Mô tả tối đa 255 ký tự") String description
) {
}
