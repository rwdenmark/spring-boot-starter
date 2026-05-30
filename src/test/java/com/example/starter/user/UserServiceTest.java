package com.example.starter.user;

import com.example.starter.common.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Fast unit tests — no Spring context, no database. Service logic only.
 * Repository and password encoder are mocked.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks UserService userService;

    private User existingUser(Long id, String email, String name) {
        var user = new User(email, name, "{bcrypt}hashed");
        // JPA assigns id and timestamps; in unit tests we reflect them in directly
        // via setters that don't exist, so we construct a fresh object and
        // rely on the saved-stub to substitute as needed.
        return user;
    }

    @Test
    void create_persistsHashedPasswordAndReturnsDto() {
        when(passwordEncoder.encode("supersecret")).thenReturn("{bcrypt}hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = userService.create(new CreateUserRequest("a@b.com", "Alice", "supersecret"));

        assertThat(result.email()).isEqualTo("a@b.com");
        assertThat(result.name()).isEqualTo("Alice");

        verify(passwordEncoder).encode("supersecret");
        verify(userRepository).save(argThat(u ->
                u.getEmail().equals("a@b.com")
                && u.getPassword().equals("{bcrypt}hashed")));
    }

    @Test
    void create_duplicateEmail_translatesToIllegalArgument() {
        when(passwordEncoder.encode(any())).thenReturn("{bcrypt}hashed");
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("unique violation"));

        assertThatThrownBy(() -> userService.create(
                new CreateUserRequest("dup@b.com", "Dup", "supersecret")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email already in use");
    }

    @Test
    void findById_returnsDto() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser(1L, "a@b.com", "Alice")));

        var result = userService.findById(1L);

        assertThat(result.email()).isEqualTo("a@b.com");
    }

    @Test
    void findById_missing_throwsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void findAll_mapsPageOfEntities() {
        var pageable = PageRequest.of(0, 10);
        var entities = List.of(existingUser(1L, "a@b.com", "A"), existingUser(2L, "b@b.com", "B"));
        when(userRepository.findAll(pageable)).thenReturn(new PageImpl<>(entities, pageable, 2));

        Page<UserResponse> page = userService.findAll(pageable);

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(UserResponse::email)
                .containsExactly("a@b.com", "b@b.com");
    }

    @Test
    void update_appliesProvidedFieldsOnly() {
        var existing = existingUser(1L, "a@b.com", "Alice");
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);

        var result = userService.update(1L, new UpdateUserRequest("Alice Updated", null));

        assertThat(result.name()).isEqualTo("Alice Updated");
        assertThat(result.email()).isEqualTo("a@b.com"); // unchanged
    }

    @Test
    void update_canChangeEmail() {
        var existing = existingUser(1L, "old@b.com", "Alice");
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);

        var result = userService.update(1L, new UpdateUserRequest(null, "new@b.com"));

        assertThat(result.email()).isEqualTo("new@b.com");
    }

    @Test
    void update_duplicateEmail_translatesToIllegalArgument() {
        var existing = existingUser(1L, "old@b.com", "Alice");
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(existing))
                .thenThrow(new DataIntegrityViolationException("unique violation"));

        assertThatThrownBy(() -> userService.update(1L,
                new UpdateUserRequest(null, "taken@b.com")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void update_missing_throwsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.update(99L, new UpdateUserRequest("x", null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void delete_removesExistingUser() {
        var existing = existingUser(1L, "a@b.com", "Alice");
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));

        userService.delete(1L);

        verify(userRepository).delete(existing);
    }

    @Test
    void delete_missing_throwsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.delete(99L))
                .isInstanceOf(NotFoundException.class);
    }
}
