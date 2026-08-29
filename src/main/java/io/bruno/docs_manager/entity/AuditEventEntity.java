package io.bruno.docs_manager.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
public class AuditEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private OffsetDateTime occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 32, updatable = false)
    private AuditAction action;

    /** Nullable and unconstrained on purpose, so the trail survives the document's deletion. */
    @Column(name = "document_id", updatable = false)
    private UUID documentId;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(name = "username", nullable = false, length = 64, updatable = false)
    private String username;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> metadata = new LinkedHashMap<>();

    protected AuditEventEntity() {}

    public AuditEventEntity(
            AuditAction action, UUID documentId, UUID userId, String username, Map<String, Object> metadata) {
        this.action = action;
        this.documentId = documentId;
        this.userId = userId;
        this.username = username;
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }

    public UUID getId() {
        return id;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public AuditAction getAction() {
        return action;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AuditEventEntity event && id != null && id.equals(event.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
