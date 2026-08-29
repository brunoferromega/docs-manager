package io.bruno.docs_manager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * A file version to register against a document. The version number is assigned by the server, not
 * supplied by the client.
 *
 * @param fileKey    reference to the object in the storage backend
 * @param checksum   checksum algorithm used for the stored object, e.g. {@code SHA-256}, {@code MD5}
 * @param uploadedBy who uploaded it; defaults to the document owner when omitted
 */
public record DocumentFileRequest(
        @NotBlank(message = "file key is required")
        @Size(max = 512, message = "file key must not exceed 512 characters")
        String fileKey,
        @NotNull(message = "checksum is required")
        @Size(max = 20, message = "checksum must not exceed 20 characters")
        String checksum,
        UUID uploadedBy) {}
