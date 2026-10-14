package com.aamotors.crm.common.error;

import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> details;
    private final List<ApiError.FieldError> fieldErrors;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public ApiException(HttpStatus status, String code, String message, Map<String, Object> details) {
        this(status, code, message, details, null);
    }

    public ApiException(HttpStatus status, String code, String message, Map<String, Object> details, List<ApiError.FieldError> fieldErrors) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
        this.fieldErrors = fieldErrors;
    }

    public static ApiException fieldError(String field, String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "validation.failed", "Validation failed", null,
                List.of(new ApiError.FieldError(field, code, message)));
    }

    public List<ApiError.FieldError> getFieldErrors() { return fieldErrors; }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public Map<String, Object> getDetails() { return details; }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "notFound", message);
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
