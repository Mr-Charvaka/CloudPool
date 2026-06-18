package com.cloudpool.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global exception handler to intercept and process exceptions across the entire application.
 * Utilizes Spring's {@code @ControllerAdvice} to provide centralized, structured error responses.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles specific bad request scenarios like IllegalArgumentExceptions.
     *
     * @param ex The intercepted exception instance.
     * @param request The metadata associated with the current web request.
     * @return A {@link ResponseEntity} containing detailed bad request metadata.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleBadRequestException(IllegalArgumentException ex, WebRequest request) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * Catches all standard unhandled runtime exceptions and returns a consistent fallback error response.
     *
     * @param ex The intercepted exception instance.
     * @param request The metadata associated with the current web request.
     * @return A {@link ResponseEntity} containing internal server error metadata.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleAllExceptions(Exception ex, WebRequest request) {
        // Keeping message generic for security reasons on 500 errors
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    /**
     * Helper method to structurally build error payloads consistently.
     */
    private ResponseEntity<Object> buildErrorResponse(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);

        return new ResponseEntity<>(body, status);
    }
}
