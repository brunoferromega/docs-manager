package io.bruno.docs_manager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateDocumentRequest(
        @NotBlank(message = "title is required")
        @Size(max = 255, message = "title must not exceed 255 characters")
        String title,
        String description) {}
