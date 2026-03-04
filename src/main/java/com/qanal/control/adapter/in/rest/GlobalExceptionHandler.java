package com.qanal.control.adapter.in.rest;

import com.qanal.control.adapter.in.rest.dto.ApiErrorResponse;
import com.qanal.control.domain.exception.InvalidTransferStateException;
import com.qanal.control.domain.exception.QuotaExceededException;
import com.qanal.control.domain.exception.TransferNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(TransferNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiErrorResponse handleNotFound(TransferNotFoundException ex, HttpServletRequest req) {
        return ApiErrorResponse.of(404, "Not Found", ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(jakarta.persistence.EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiErrorResponse handleEntityNotFound(jakarta.persistence.EntityNotFoundException ex, HttpServletRequest req) {
        return ApiErrorResponse.of(404, "Not Found", ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(QuotaExceededException.class)
    @ResponseStatus(HttpStatus.PAYMENT_REQUIRED)
    public ApiErrorResponse handleQuota(QuotaExceededException ex, HttpServletRequest req) {
        return ApiErrorResponse.of(402, "Quota Exceeded", ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest req) {
        return ApiErrorResponse.of(400, "Bad Request", ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(InvalidTransferStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse handleInvalidState(InvalidTransferStateException ex, HttpServletRequest req) {
        return ApiErrorResponse.of(409, "Conflict", ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse handleIllegalState(IllegalStateException ex, HttpServletRequest req) {
        return ApiErrorResponse.of(409, "Conflict", ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ApiErrorResponse.of(400, "Validation Failed", details, req.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiErrorResponse handleGeneral(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception on {}", req.getRequestURI(), ex);
        return ApiErrorResponse.of(500, "Internal Server Error",
                "An unexpected error occurred", req.getRequestURI());
    }
}
