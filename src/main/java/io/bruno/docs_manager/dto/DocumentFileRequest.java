package io.bruno.docs_manager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * A file version to register against a document. The version number is assigned by the server, not
 * supplied by the client.
 *
 * @param fileKey    reference to the object in the storage backend
 * @param checksum   SHA-256 of the stored object, as 64 hex characters
 * @param uploadedBy who uploaded it; defaults to the document owner when omitted
 */
public record DocumentFileRequest(
        @NotBlank(message = "file key is required")
        @Size(max = 512, message = "file key must not exceed 512 characters")
        String fileKey,
        @NotBlank(message = "checksum is required")
        @Pattern(regexp = "^[A-Fa-f0-9]{64}$", message = "checksum must be 64 hexadecimal characters")
        String checksum,
        UUID uploadedBy) {}
