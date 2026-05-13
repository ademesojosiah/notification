package com.hireflow.notification.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EmailDeliveryException.class)
    public ResponseEntity<Map<String, String>> handleEmailDelivery(EmailDeliveryException ex, HttpServletRequest request) {
        log.error("Email delivery failure at {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "Email delivery failed", "message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Invalid argument at {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Bad request", "message", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneral(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "An unexpected error occurred"));
    }
}
