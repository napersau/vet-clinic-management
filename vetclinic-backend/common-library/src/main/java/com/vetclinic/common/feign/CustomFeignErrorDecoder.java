package com.vetclinic.common.feign;

import com.vetclinic.common.dto.ApiResponse;
import com.vetclinic.common.exception.AppException;
import com.vetclinic.common.exception.ErrorCode;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;

/**
 * Khôi phục chính xác ErrorCode mà service con trả về trong ApiResponse,
 * để lỗi nghiệp vụ (vd. PET_NOT_FOUND) đi xuyên qua chuỗi gọi Feign thay vì biến thành 503.
 */
@Slf4j
@RequiredArgsConstructor
public class CustomFeignErrorDecoder implements ErrorDecoder {

    private final ObjectMapper objectMapper;

    @Override
    public Exception decode(String methodKey, Response response) {
        if (response.body() != null) {
            try (InputStream body = response.body().asInputStream()) {
                ApiResponse<?> errorResponse = objectMapper.readValue(body, ApiResponse.class);
                ErrorCode errorCode = ErrorCode.fromCode(errorResponse.getErrorCode());
                if (errorCode != null) {
                    return new AppException(errorCode, errorResponse.getMessage());
                }
            } catch (Exception e) {
                log.warn("Không đọc được body lỗi từ Feign [{}] status={}: {}", methodKey, response.status(), e.getMessage());
            }
        }
        return new AppException(fallbackFor(response.status()));
    }

    private static ErrorCode fallbackFor(int status) {
        return switch (status) {
            case 400 -> ErrorCode.INVALID_INPUT_DATA;
            case 401 -> ErrorCode.UNAUTHORIZED;
            case 403 -> ErrorCode.ACCESS_DENIED;
            case 404 -> ErrorCode.RESOURCE_NOT_FOUND;
            case 409 -> ErrorCode.OPTIMISTIC_LOCK_CONFLICT;
            default -> ErrorCode.SERVICE_UNAVAILABLE;
        };
    }
}
