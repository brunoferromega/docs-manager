package io.bruno.docs_manager.dto;

import io.bruno.docs_manager.entity.UserRole;

import java.util.UUID;

/** @param expiresIn lifetime of the token in seconds */
public record LoginResponse(
        String accessToken, String tokenType, long expiresIn, UUID userId, String username, UserRole role) {}
