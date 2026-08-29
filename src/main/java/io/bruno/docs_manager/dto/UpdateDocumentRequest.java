package io.bruno.docs_manager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateDocumentRequest(
        @NotBlank(message = "title is required")
        @Size(max = 255, message = "title must not exceed 255 characters")
        String title,
        String description,
        @Size(max = 20, message = "a document cannot have more than 20 tags")
        Set<@Size(max = 64, message = "a tag must not exceed 64 characters") String> tags) {}
