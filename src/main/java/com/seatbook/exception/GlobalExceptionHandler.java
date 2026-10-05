package com.seatbook.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> api(ApiException e) {
        return ResponseEntity.status(e.status).body(Map.of("error", e.reason, "reason", e.reason, "message", String.valueOf(e.getMessage())));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> badJson(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "invalid-request", "message", "malformed JSON body"));
    }

    @ExceptionHandler(CannotGetJdbcConnectionException.class)
    public ResponseEntity<Map<String, Object>> noDb(CannotGetJdbcConnectionException e) {
        log.error("database unavailable", e);
        return ResponseEntity.status(503).header("Retry-After", "1")
                .body(Map.of("error", "unavailable", "message", "database unavailable"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> other(Exception e) {
        if (e instanceof ErrorResponse er) { // Spring MVC's own 4xx (404/405/415/missing header...)
            return ResponseEntity.status(er.getStatusCode()).body(Map.of("error", "http-" + er.getStatusCode().value(), "message", String.valueOf(e.getMessage())));
        }
        log.error("unhandled", e);
        return ResponseEntity.status(500).body(Map.of("error", "internal", "message", "internal error"));
    }
}
