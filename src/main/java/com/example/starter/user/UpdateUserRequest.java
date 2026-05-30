package com.example.starter.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Partial-update payload. Every field is optional — only fields that are
 * present (non-null) are applied. Use a PATCH request to send this.
 */
public record UpdateUserRequest(
        @Size(min = 1, max = 255) String name,
        @Email String email
) {}
