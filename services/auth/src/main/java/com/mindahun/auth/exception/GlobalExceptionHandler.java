package com.mindahun.auth.exception;

import com.mindahun.auth.exception.custom.UserAlreadyExistsException;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<?> handleUserAlreadyExistsException(UserAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", e.getMessage(),
                "timestamp",LocalDateTime.now()
        ));
    }

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<Map<String, Object>> handleFeignException(FeignException ex) {
        log.info("FeignException HAPPENING: {}", ex.getMessage());
        Map<String, Object> body = new HashMap<>();
        body.put("error", "Error calling downstream service");
        body.put("message", ex.contentUTF8());
        body.put("status", ex.status());
        body.put("service", extractServiceName(ex));

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        if (ex.status() == 404) status = HttpStatus.NOT_FOUND;
        else if (ex.status() == 400) status = HttpStatus.BAD_REQUEST;

        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", ex.getMessage());
        body.put("timestamp", LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private String extractServiceName(FeignException ex) {
        String msg = ex.getMessage();
        if (msg != null && msg.contains("USER-SERVICE")) return "USER-SERVICE";
        return "unknown";
    }
}
