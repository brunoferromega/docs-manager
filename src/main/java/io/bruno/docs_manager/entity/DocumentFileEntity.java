package io.bruno.docs_manager.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "document_files",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_document_version",
                        columnNames = {"document_id", "version_number"}))
public class DocumentFileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private DocumentEntity document;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "file_key", nullable = false, length = 512)
    private String fileKey;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "checksum", nullable = false, length = 64)
    private String checksum;

    @CreationTimestamp
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private OffsetDateTime uploadedAt;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    protected DocumentFileEntity() {}

    public DocumentFileEntity(Integer versionNumber, String fileKey, String checksum, UUID uploadedBy) {
        this.versionNumber = versionNumber;
        this.fileKey = fileKey;
        this.checksum = checksum;
        this.uploadedBy = uploadedBy;
    }

    public UUID getId() {
        return id;
    }

    public DocumentEntity getDocument() {
        return document;
    }

    void setDocument(DocumentEntity document) {
        this.document = document;
    }

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(Integer versionNumber) {
        this.versionNumber = versionNumber;
    }

    public String getFileKey() {
        return fileKey;
    }

    public void setFileKey(String fileKey) {
        this.fileKey = fileKey;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public OffsetDateTime getUploadedAt() {
        return uploadedAt;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(UUID uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DocumentFileEntity file
                && id != null
                && id.equals(file.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
