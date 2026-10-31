package com.vetclinic.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vetclinic.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private int status;            // Mã HTTP Status (200, 201, 400, 403, 404, 500)
    private String errorCode;      // Mã lỗi nghiệp vụ (VD: "CLIN_1302"), null nếu thành công
    private String message;        // Thông điệp phản hồi thân thiện
    private T data;                // Dữ liệu payload nghiệp vụ
    @Builder.Default
    private Instant timestamp = Instant.now();

    public static <T> ApiResponse<T> success(T data) {
        return success("Thao tác thành công", data);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .status(200)
                .message(message)
                .data(data)
                .build();
    }

    // Controller phải trả kèm ResponseEntity.status(HttpStatus.CREATED) để header HTTP cũng là 201
    public static <T> ApiResponse<T> created(String message, T data) {
        return ApiResponse.<T>builder()
                .status(201)
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        return ApiResponse.<T>builder()
                .status(errorCode.getHttpStatus().value())
                .errorCode(errorCode.getCode())
                .message(message != null ? message : errorCode.getMessage())
                .build();
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return error(errorCode, null);
    }
}
