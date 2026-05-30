package com.example.starter.user;

import com.example.starter.common.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        log.info("Creating user with email {}", request.email());
        var user = new User(
                request.email(),
                request.name(),
                passwordEncoder.encode(request.password())
        );
        try {
            var saved = userRepository.save(user);
            log.info("Created user {} (email {})", saved.getId(), saved.getEmail());
            return UserResponse.from(saved);
        } catch (DataIntegrityViolationException ex) {
            // Rely on the DB unique constraint rather than a pre-check to avoid races
            log.warn("Email already in use: {}", request.email());
            throw new IllegalArgumentException("Email already in use");
        }
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        log.debug("Looking up user by id {}", id);
        var user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> findAll(Pageable pageable) {
        log.debug("Listing users: {}", pageable);
        return userRepository.findAll(pageable).map(UserResponse::from);
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        log.info("Updating user {}", id);
        var user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));
        if (request.name() != null) {
            user.setName(request.name());
        }
        if (request.email() != null && !request.email().equals(user.getEmail())) {
            user.changeEmail(request.email());
        }
        try {
            return UserResponse.from(userRepository.save(user));
        } catch (DataIntegrityViolationException ex) {
            log.warn("Email already in use: {}", request.email());
            throw new IllegalArgumentException("Email already in use");
        }
    }

    @Transactional
    public void delete(Long id) {
        log.info("Deleting user {}", id);
        var user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));
        userRepository.delete(user);
    }
}
