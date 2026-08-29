package io.bruno.docs_manager.dto;

import io.bruno.docs_manager.entity.DocumentEntity;
import io.bruno.docs_manager.entity.DocumentStatus;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        String title,
        String description,
        Set<String> tags,
        UUID ownerId,
        DocumentStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static DocumentResponse from(DocumentEntity document) {
        return new DocumentResponse(
                document.getId(),
                document.getTitle(),
                document.getDescription(),
                document.getTags(),
                document.getOwnerId(),
                document.getStatus(),
                toUtc(document.getCreatedAt()),
                toUtc(document.getUpdatedAt()));
    }

    /**
     * Hibernate-generated timestamps carry the JVM offset while ones read back from Postgres carry UTC;
     * normalising keeps the payload consistent.
     */
    private static OffsetDateTime toUtc(OffsetDateTime timestamp) {
        return timestamp == null ? null : timestamp.withOffsetSameInstant(ZoneOffset.UTC);
    }
}
