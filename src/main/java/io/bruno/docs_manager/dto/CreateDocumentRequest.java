package io.bruno.docs_manager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateDocumentRequest(
        @NotBlank(message = "title is required")
        @Size(max = 255, message = "title must not exceed 255 characters")
        String title,
        String description,
        @NotNull(message = "owner is required") UUID ownerId) {}
