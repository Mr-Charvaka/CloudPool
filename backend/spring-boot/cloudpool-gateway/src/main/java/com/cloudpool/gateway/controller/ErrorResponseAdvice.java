package com.cloudpool.gateway.controller;

import com.cloudpool.exception.GlobalExceptionHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.net.URI;

@ControllerAdvice
public class ErrorResponseAdvice {

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleException(Exception ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setType(URI.create("https://docs.cloudpool.dev/errors/internal-error"));
        problem.setTitle("Internal Server Error");
        problem.setDetail("An unexpected error occurred");
        problem.setProperty("traceId", org.slf4j.MDC.get("traceId"));
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadRequest(IllegalArgumentException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(URI.create("https://docs.cloudpool.dev/errors/validation-error"));
        problem.setTitle("Validation Error");
        problem.setDetail(ex.getMessage());
        problem.setProperty("traceId", org.slf4j.MDC.get("traceId"));
        return problem;
    }

    @ExceptionHandler(SecurityException.class)
    public ProblemDetail handleSecurity(SecurityException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problem.setType(URI.create("https://docs.cloudpool.dev/errors/security-error"));
        problem.setTitle("Forbidden");
        problem.setDetail(ex.getMessage());
        problem.setProperty("traceId", org.slf4j.MDC.get("traceId"));
        return problem;
    }
}