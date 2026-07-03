package com.example.starter.common;

/**
 * Thrown when a create or update would reuse an email that is already taken.
 * Maps to 400 in {@link GlobalExceptionHandler}.
 */
public class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException(String message) {
        super(message);
    }
}
