package com.example.starter.user;

import com.example.starter.common.NotFoundException;
import com.example.starter.user.UserDtos.CreateUserRequest;
import com.example.starter.user.UserDtos.UpdateUserRequest;
import com.example.starter.user.UserDtos.UserResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserResponse create(CreateUserRequest request) {
        log.info("Creating user with email {}", request.email());
        if (userRepository.existsByEmail(request.email())) {
            log.warn("Email already in use: {}", request.email());
            throw new IllegalArgumentException("Email already in use");
        }
        var user = new User(
                request.email(),
                request.name(),
                passwordEncoder.encode(request.password())
        );
        var saved = userRepository.save(user);
        log.info("Created user {} (email {})", saved.getId(), saved.getEmail());
        return UserResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        log.debug("Looking up user by id {}", id);
        var user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        log.debug("Listing all users");
        return userRepository.findAll().stream()
                .map(UserResponse::from)
                .toList();
    }

    public UserResponse update(Long id, UpdateUserRequest request) {
        log.info("Updating user {}", id);
        var user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));
        user.setName(request.name());
        return UserResponse.from(userRepository.save(user));
    }

    public void delete(Long id) {
        log.info("Deleting user {}", id);
        var user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));
        userRepository.delete(user);
    }
}