package com.tolox.templateservice.exception;

import com.tolox.templateservice.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateKeyException(DuplicateKeyException e) {
        log.warn("Duplicate key violation: {}", e.getMessage());
        ErrorResponse error = new ErrorResponse(
                "A template or version with these unique attributes already exists.",
                "DUPLICATE_ENTRY",
                Instant.now()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDatabaseError(org.springframework.dao.DataAccessException ex) {
        log.error("Database error occurred: ", ex);
        ErrorResponse error = new ErrorResponse(
                "A database error occurred while processing your request.",
                "DATABASE_ERROR",
                Instant.now()
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalError(Exception ex) {
        log.error("Unhandled exception: ", ex);
        ErrorResponse error = new ErrorResponse(
                "An unexpected internal error occurred.",
                "INTERNAL_SERVER_ERROR",
                Instant.now()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
