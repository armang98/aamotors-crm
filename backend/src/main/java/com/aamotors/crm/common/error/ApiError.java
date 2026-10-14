package com.aamotors.crm.common.error;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Stable error envelope returned by every endpoint.
 * {@code code} is a machine key the frontend maps to a localised message.
 */
public record ApiError(
        int status,
        String code,
        String message,
        List<FieldError> fieldErrors,
        Map<String, Object> details,
        Instant timestamp
) {
    public record FieldError(String field, String code, String message) {}

    public static ApiError of(int status, String code, String message) {
        return new ApiError(status, code, message, null, null, Instant.now());
    }
}
