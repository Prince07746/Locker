package com.guardian.app.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

public class ApiExceptions {

    public static class ApiException extends RuntimeException {
        private final HttpStatus status;
        public ApiException(HttpStatus status, String message) {
            super(message);
            this.status = status;
        }
        public HttpStatus getStatus() { return status; }
    }

    public static ApiException notFound(String msg) { return new ApiException(HttpStatus.NOT_FOUND, msg); }
    public static ApiException unauthorized(String msg) { return new ApiException(HttpStatus.UNAUTHORIZED, msg); }
    public static ApiException conflict(String msg) { return new ApiException(HttpStatus.CONFLICT, msg); }
    public static ApiException badRequest(String msg) { return new ApiException(HttpStatus.BAD_REQUEST, msg); }

    @RestControllerAdvice
    public static class Handler {
        @ExceptionHandler(ApiException.class)
        public ResponseEntity<Map<String, Object>> handle(ApiException e) {
            return ResponseEntity.status(e.getStatus())
                    .body(Map.of("error", e.getMessage(), "status", e.getStatus().value()));
        }
    }
}
