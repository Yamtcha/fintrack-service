package com.fintrack.api.exception;

import com.fintrack.common.dto.ErrorResponse;
import com.fintrack.common.exception.DuplicateTransactionException;
import com.fintrack.common.exception.InvalidSourceTypeException;
import com.fintrack.common.exception.SourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSourceNotFound(SourceNotFoundException ex) {
        log.error("Source not found: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, "SOURCE_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(InvalidSourceTypeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSourceType(InvalidSourceTypeException ex) {
        log.error("Invalid source type: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "INVALID_SOURCE_TYPE", ex.getMessage());
    }

    @ExceptionHandler(DuplicateTransactionException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateTransaction(DuplicateTransactionException ex) {
        log.error("Duplicate transaction: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, "DUPLICATE_TRANSACTION", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.error("Validation failed: {}", details);
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", details);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
        log.error("Authentication failure: {}", ex.getMessage());
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        log.error("Access denied: {}", ex.getMessage());
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred");
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(
                new ErrorResponse(code, message, UUID.randomUUID().toString(), Instant.now()));
    }
}
