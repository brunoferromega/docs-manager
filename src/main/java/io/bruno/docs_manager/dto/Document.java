package io.bruno.docs_manager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record Document(
        @NotBlank(message = "title is required") String title,
        String description,
        List<String> tags,
        @NotNull(message = "owner is required") UUID ownerId,
        @NotNull(message = "status is required") DocStatus status,
        DocumentFile file) {

    public enum DocStatus {
        DRAFT,
        PUBLISHED,
        ARCHIVED;
    }

    public record DocumentFile(
            @NotBlank(message = "file key is required") String fileKey,
            @NotBlank(message = "checksum is required") String checksum) {}
}
