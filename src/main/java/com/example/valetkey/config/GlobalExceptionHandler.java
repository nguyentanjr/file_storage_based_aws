package com.example.valetkey.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Map<String, Object> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        // Log only if it's not a common browser request (like favicon or OPTIONS)
        String method = ex.getMethod();
        if (!"OPTIONS".equals(method) && !ex.getMessage().contains("favicon")) {
            log.warn("Method not supported: {} for URL: {}", method, ex.getMessage());
        }
        return Map.of(
            "error", "Method Not Allowed",
            "message", "The HTTP method " + method + " is not supported for this endpoint"
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, Object> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return Map.of(
            "error", "Forbidden",
            "message", "You don't have permission to access this resource"
        );
    }

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, Object> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Bad credentials attempt");
        return Map.of(
            "error", "Unauthorized",
            "message", "Invalid username or password"
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return Map.of(
            "error", "Bad Request",
            "message", ex.getMessage()
        );
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleRuntimeException(RuntimeException ex) {
        log.error("Runtime exception occurred", ex);
        return Map.of(
            "error", "Internal Server Error",
            "message", "An unexpected error occurred. Please try again later."
        );
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleGenericException(Exception ex) {
        log.error("Unexpected exception occurred", ex);
        return Map.of(
            "error", "Internal Server Error",
            "message", "An unexpected error occurred. Please contact support if the problem persists."
        );
    }
}




