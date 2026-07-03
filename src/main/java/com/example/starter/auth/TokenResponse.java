package com.example.starter.auth;

import java.time.Instant;

public record TokenResponse(String token, Instant expiresAt) {}
