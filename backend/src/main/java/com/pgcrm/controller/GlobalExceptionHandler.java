package com.pgcrm.controller;

import com.pgcrm.exception.ResourceNotFoundException;
import com.pgcrm.exception.BedUnavailableException;
import com.pgcrm.exception.InvalidLockoutException;
import com.pgcrm.exception.SignatureVerificationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleResourceNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error(ex.getMessage(), 404));
    }

    @ExceptionHandler(BedUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleBedUnavailable(BedUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error(ex.getMessage(), 400));
    }

    @ExceptionHandler(InvalidLockoutException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidLockout(InvalidLockoutException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error(ex.getMessage(), 400));
    }

    @ExceptionHandler(SignatureVerificationException.class)
    public ResponseEntity<Map<String, Object>> handleSignatureVerification(SignatureVerificationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error(ex.getMessage(), 400));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntime(RuntimeException ex) {
        return ResponseEntity.badRequest().body(error(ex.getMessage(), 400));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error("Access denied", 403));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        return ResponseEntity.internalServerError().body(error("Internal server error: " + ex.getMessage(), 500));
    }

    private Map<String, Object> error(String message, int status) {
        return Map.of("error", message, "status", status, "timestamp", LocalDateTime.now().toString());
    }
}
