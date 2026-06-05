package com.devsecops.usermgmt.exception;

/**
 * Custom access-denied exception used by the service layer to enforce
 * object-level authorization (BOLA defense). Distinct from
 * {@code org.springframework.security.access.AccessDeniedException} so
 * that the {@link GlobalExceptionHandler} can map it cleanly.
 */
public class AccessDeniedException extends RuntimeException {

    public AccessDeniedException(String message) {
        super(message);
    }

    public AccessDeniedException(String message, Throwable cause) {
        super(message, cause);
    }
}
