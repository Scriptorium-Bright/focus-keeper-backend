package com.adhd.focusmate.common.dto;

import com.adhd.focusmate.common.exception.ErrorCode;
import lombok.Builder;
import org.springframework.http.ResponseEntity;

@Builder
public record ErrorResponse(
        boolean success,
        String message,
        String errorCode) {
    public static ResponseEntity<ErrorResponse> toResponseEntity(ErrorCode errorCode) {
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.builder()
                        .success(false)
                        .message(errorCode.getMessage())
                        .errorCode(errorCode.name())
                        .build());
    }

    public static ResponseEntity<ErrorResponse> toResponseEntity(ErrorCode errorCode, String message) {
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.builder()
                        .success(false)
                        .message(message)
                        .errorCode(errorCode.name())
                        .build());
    }
}
