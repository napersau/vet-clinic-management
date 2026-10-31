package com.vetclinic.common.exception;

import com.vetclinic.common.dto.ApiResponse;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Chỉ nạp ở service có OpenFeign. Lỗi nghiệp vụ từ service con đã được CustomFeignErrorDecoder
 * chuyển thành AppException; handler này chỉ còn bắt lỗi kết nối/timeout.
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "feign.Feign")
public class FeignExceptionHandler {

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ApiResponse<Object>> handleFeignException(FeignException ex) {
        log.error("Feign client communication error: status = {}, message = {}", ex.status(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.error(ErrorCode.SERVICE_UNAVAILABLE,
                "Không thể kết nối đến dịch vụ phụ thuộc, vui lòng thử lại sau"));
    }
}
