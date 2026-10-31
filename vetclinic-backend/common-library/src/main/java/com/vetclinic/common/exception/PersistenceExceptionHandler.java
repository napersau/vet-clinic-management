package com.vetclinic.common.exception;

import com.vetclinic.common.dto.ApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Chỉ nạp ở service có JPA (notification-service không có spring-orm).
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "org.springframework.orm.ObjectOptimisticLockingFailureException")
public class PersistenceExceptionHandler {

    // @Version phát hiện hai người cùng sửa một bản ghi; PessimisticLockingFailureException gồm hết thời gian chờ khóa
    // dòng và deadlock của MySQL (người dùng thử lại là được) nên trả 409 thay vì 500
    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<ApiResponse<Object>> handleOptimisticLock(Exception ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ErrorCode.OPTIMISTIC_LOCK_CONFLICT));
    }
}
