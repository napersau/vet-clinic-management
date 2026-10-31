package com.vetclinic.auth.dto.request;

import com.vetclinic.auth.constant.ValidationPatterns;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * ADMIN cập nhật nhân sự. Trường nào null thì giữ nguyên. roomNumber là chuỗi rỗng thì gỡ phòng.
 */
public record UpdateUserRequest(
        @Size(min = 1, max = 150, message = "Họ tên từ 1 đến 150 ký tự")
        String fullName,

        @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)
        String phoneNumber,

        @Email(message = "Email không hợp lệ")
        @Size(max = 150, message = "Email tối đa 150 ký tự")
        String email,

        @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
        String address,

        @Pattern(regexp = "^$|" + ValidationPatterns.ROOM_NUMBER, message = ValidationPatterns.ROOM_MESSAGE)
        String roomNumber
) {
}
