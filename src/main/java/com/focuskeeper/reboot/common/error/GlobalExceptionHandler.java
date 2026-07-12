package com.focuskeeper.reboot.common.error;

import com.focuskeeper.reboot.common.response.ErrorResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// high
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final Map<String, Map<String, String>> CONSTRAINT_CONFLICT_DETAILS = Map.of(
            "uq_recovery_session_active",
            Map.of("resource", "recoverySession", "reason", "ACTIVE_SESSION_ALREADY_EXISTS"),
            "uq_daily_big3_entry_order",
            Map.of("resource", "dailyBig3Entry", "reason", "ACTIVE_SLOT_ALREADY_EXISTS"),
            "uq_daily_big3_entry_item",
            Map.of("resource", "dailyBig3Entry", "reason", "ACTIVE_ITEM_ALREADY_EXISTS"),
            "uq_big3_items_derived_from_item",
            Map.of("resource", "big3Item", "reason", "CARRYOVER_ALREADY_EXISTS"),
            "uk_daily_big3_boards_user_date",
            Map.of("resource", "dailyBig3Board", "reason", "BOARD_ALREADY_EXISTS")
    );

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        ErrorResponse response = ErrorResponse.of(
                errorCode.getCode(),
                errorCode.getMessage(),
                exception.getDetails()
        );
        return ResponseEntity.status(errorCode.getStatus()).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception
    ) {
        Map<String, String> details = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            details.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        ErrorResponse response = ErrorResponse.of(
                ErrorCode.COMMON_BAD_REQUEST.getCode(),
                ErrorCode.COMMON_BAD_REQUEST.getMessage(),
                details
        );
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception
    ) {
        ErrorResponse response = ErrorResponse.of(
                ErrorCode.COMMON_BAD_REQUEST.getCode(),
                ErrorCode.COMMON_BAD_REQUEST.getMessage(),
                Map.of("requestBody", "요청 본문이 올바른 JSON 형식이 아닙니다.")
        );
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(
            DataIntegrityViolationException exception
    ) {
        String constraintName = findConstraintName(exception);
        Map<String, String> details = CONSTRAINT_CONFLICT_DETAILS.get(constraintName);
        if (details == null) {
            return internalServerError(exception);
        }

        ErrorResponse response = ErrorResponse.of(
                ErrorCode.CONFLICT.getCode(),
                ErrorCode.CONFLICT.getMessage(),
                details
        );
        return ResponseEntity.status(ErrorCode.CONFLICT.getStatus()).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception exception) {
        return internalServerError(exception);
    }

    private ResponseEntity<ErrorResponse> internalServerError(Exception exception) {
        log.error("Unhandled exception", exception);
        ErrorResponse response = ErrorResponse.of(
                ErrorCode.SYSTEM_INTERNAL_ERROR.getCode(),
                ErrorCode.SYSTEM_INTERNAL_ERROR.getMessage(),
                null
        );
        return ResponseEntity.internalServerError().body(response);
    }

    private String findConstraintName(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolationException
                    && constraintViolationException.getConstraintName() != null) {
                return constraintViolationException.getConstraintName();
            }

            String message = current.getMessage();
            if (message != null) {
                for (String constraintName : CONSTRAINT_CONFLICT_DETAILS.keySet()) {
                    if (message.contains(constraintName)) {
                        return constraintName;
                    }
                }
            }
            current = current.getCause();
        }
        return null;
    }
}
