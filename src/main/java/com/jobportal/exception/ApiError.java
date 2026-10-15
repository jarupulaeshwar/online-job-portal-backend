package com.jobportal.exception;

import java.time.LocalDateTime;
import java.util.Map;

/** Standard JSON error body returned by every failing request. */
public record ApiError(LocalDateTime timestamp, int status, String message, Map<String, String> fieldErrors) {
    public static ApiError of(int status, String message, Map<String, String> fieldErrors) {
        return new ApiError(LocalDateTime.now(), status, message, fieldErrors);
    }
}
