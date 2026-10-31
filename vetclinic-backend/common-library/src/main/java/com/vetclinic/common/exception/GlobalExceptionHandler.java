package com.vetclinic.common.exception;

import com.vetclinic.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bộ bắt lỗi tập trung cho các service Servlet (Gateway WebFlux không nạp class này).
 * Đặt thứ tự thấp nhất để các advice chuyên biệt (Feign, Optimistic Lock) được ưu tiên trước handler Exception chung.
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class GlobalExceptionHandler {

    // 1. Ngoại lệ nghiệp vụ do ứng dụng ném ra
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Object>> handleAppException(AppException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiResponse.error(errorCode, ex.getMessage()));
    }

    // 2. Lỗi phân quyền @PreAuthorize -> HTTP 403
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDeniedException(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(ErrorCode.ACCESS_DENIED));
    }

    // 3. Lỗi Validation DTO (@Valid)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ApiResponse<Map<String, String>> response = ApiResponse.error(ErrorCode.INVALID_INPUT_DATA);
        response.setData(errors);
        return ResponseEntity.badRequest().body(response);
    }

    // 4. JSON gửi lên sai cú pháp hoặc thiếu body
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.INVALID_INPUT_DATA,
                "Định dạng dữ liệu JSON không hợp lệ hoặc thiếu Body"));
    }

    // 4b. Thiếu tham số / sai kiểu tham số (vd. status=ABC, date=hôm-nay) là lỗi của client, không phải lỗi hệ thống
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingParameter(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.INVALID_INPUT_DATA,
                "Thiếu tham số bắt buộc: " + ex.getParameterName()));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingPart(MissingServletRequestPartException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.INVALID_INPUT_DATA,
                "Thiếu phần dữ liệu bắt buộc: " + ex.getRequestPartName()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.INVALID_INPUT_DATA,
                "Giá trị của tham số '" + ex.getName() + "' không hợp lệ"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.INVALID_IMAGING_FILE));
    }

    // 5. Đường dẫn không tồn tại
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND));
    }

    // 6. Sai phương thức HTTP
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(ApiResponse.error(ErrorCode.METHOD_NOT_ALLOWED));
    }

    // 7. Lỗi hệ thống không xác định: log chi tiết ở server, ẩn stacktrace với client
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGeneralException(Exception ex) {
        log.error("Unhandled system error: ", ex);
        return ResponseEntity.internalServerError().body(ApiResponse.error(ErrorCode.UNCATEGORIZED_EXCEPTION,
                "Hệ thống phát sinh sự cố, quản trị viên đã ghi nhận để khắc phục"));
    }
}
