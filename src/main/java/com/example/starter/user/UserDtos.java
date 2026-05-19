package com.example.starter.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Records replace 90% of POJO boilerplate. Immutable, with auto-generated
 * equals/hashCode/toString and accessor methods.
 */
public class UserDtos {

    public record CreateUserRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 1, max = 255) String name,
            @NotBlank @Size(min = 8, max = 100) String password
    ) {}

    public record UserResponse(
            Long id,
            String email,
            String name,
            String role,
            Instant createdAt
    ) {
        public static UserResponse from(User user) {
            return new UserResponse(
                    user.getId(),
                    user.getEmail(),
                    user.getName(),
                    user.getRole().name(),
                    user.getCreatedAt()
            );
        }
    }
}
