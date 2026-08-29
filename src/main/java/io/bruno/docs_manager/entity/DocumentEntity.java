package io.bruno.docs_manager.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "documents")
public class DocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "document_status")
    private DocumentStatus status = DocumentStatus.DRAFT;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "document_tags", joinColumns = @JoinColumn(name = "document_id"))
    @Column(name = "tag", nullable = false, length = 64)
    @BatchSize(size = 50) // Loads the tags of a whole page in one extra query instead of one per row.
    private Set<String> tags = new LinkedHashSet<>();

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("versionNumber ASC")
    private List<DocumentFileEntity> files = new ArrayList<>();

    protected DocumentEntity() {}

    public DocumentEntity(String title, String description, UUID ownerId, DocumentStatus status) {
        this.title = title;
        this.description = description;
        this.ownerId = ownerId;
        this.status = status;
    }

    public void addFile(DocumentFileEntity file) {
        files.add(file);
        file.setDocument(this);
    }

    public void removeFile(DocumentFileEntity file) {
        files.remove(file);
        file.setDocument(null);
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public void setStatus(DocumentStatus status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public Set<String> getTags() {
        return Collections.unmodifiableSet(tags);
    }

    /** Replaces the whole set; values are normalised so lookups can hit {@code idx_document_tags_tag} directly. */
    public void setTags(Collection<String> tags) {
        this.tags.clear();
        if (tags == null) {
            return;
        }
        for (String tag : tags) {
            String normalised = normaliseTag(tag);
            if (!normalised.isEmpty()) {
                this.tags.add(normalised);
            }
        }
    }

    public static String normaliseTag(String tag) {
        return tag == null ? "" : tag.strip().toLowerCase(Locale.ROOT);
    }

    public List<DocumentFileEntity> getFiles() {
        return files;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DocumentEntity document
                && id != null
                && id.equals(document.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
