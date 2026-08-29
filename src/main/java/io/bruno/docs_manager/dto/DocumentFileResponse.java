package io.bruno.docs_manager.dto;

import io.bruno.docs_manager.entity.DocumentFileEntity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

public record DocumentFileResponse(
        UUID id,
        UUID documentId,
        Integer versionNumber,
        String fileKey,
        String checksum,
        OffsetDateTime uploadedAt,
        UUID uploadedBy) {

    public static DocumentFileResponse from(DocumentFileEntity file) {
        return new DocumentFileResponse(
                file.getId(),
                file.getDocument().getId(),
                file.getVersionNumber(),
                file.getFileKey(),
                // CHAR(64) comes back space-padded when a shorter value ever slips in.
                file.getChecksum() == null ? null : file.getChecksum().strip(),
                file.getUploadedAt() == null ? null : file.getUploadedAt().withOffsetSameInstant(ZoneOffset.UTC),
                file.getUploadedBy());
    }
}
