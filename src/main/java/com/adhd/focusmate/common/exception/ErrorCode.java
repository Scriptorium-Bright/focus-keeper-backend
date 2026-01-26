package com.adhd.focusmate.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // Common
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error"),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "Invalid Input"),

    // Auth
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Unauthorized access"),

    // Domain specific
    ENTITY_NOT_FOUND(HttpStatus.NOT_FOUND, "Entity not found"),
    INSUFFICIENT_BALANCE(HttpStatus.BAD_REQUEST, "Insufficient wallet balance"),

    // Task
    TASK_ALREADY_COMPLETED(HttpStatus.CONFLICT, "Task is already completed"),
    TASK_ALREADY_FINALIZED(HttpStatus.CONFLICT, "Task is already finalized"),
    INVALID_TASK_STATUS(HttpStatus.BAD_REQUEST, "Invalid task status for this operation");

    private final HttpStatus status;
    private final String message;
}
