package io.bruno.docs_manager.service;

import io.bruno.docs_manager.dto.CreateDocumentRequest;
import io.bruno.docs_manager.dto.DocumentFileRequest;
import io.bruno.docs_manager.dto.DocumentFileResponse;
import io.bruno.docs_manager.dto.DocumentFilter;
import io.bruno.docs_manager.dto.DocumentResponse;
import io.bruno.docs_manager.dto.UpdateDocumentRequest;
import io.bruno.docs_manager.entity.AuditAction;
import io.bruno.docs_manager.entity.DocumentEntity;
import io.bruno.docs_manager.entity.DocumentFileEntity;
import io.bruno.docs_manager.entity.DocumentStatus;
import io.bruno.docs_manager.exception.DocumentNotFoundException;
import io.bruno.docs_manager.exception.FileVersionNotFoundException;
import io.bruno.docs_manager.exception.InvalidFilterException;
import io.bruno.docs_manager.exception.InvalidStatusTransitionException;
import io.bruno.docs_manager.repository.DocumentFileRepository;
import io.bruno.docs_manager.repository.DocumentRepository;
import io.bruno.docs_manager.repository.DocumentSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DocumentService {

    private static final int FIRST_VERSION = 1;

    private static final Map<DocumentStatus, Set<DocumentStatus>> ALLOWED_TRANSITIONS = Map.of(
            DocumentStatus.DRAFT, EnumSet.of(DocumentStatus.PUBLISHED, DocumentStatus.ARCHIVED),
            DocumentStatus.PUBLISHED, EnumSet.of(DocumentStatus.ARCHIVED),
            DocumentStatus.ARCHIVED, EnumSet.noneOf(DocumentStatus.class));

    private final DocumentRepository documentRepository;
    private final DocumentFileRepository documentFileRepository;
    private final AuditService auditService;

    public DocumentService(
            DocumentRepository documentRepository,
            DocumentFileRepository documentFileRepository,
            AuditService auditService) {
        this.documentRepository = documentRepository;
        this.documentFileRepository = documentFileRepository;
        this.auditService = auditService;
    }

    /** Documents always start as {@link DocumentStatus#DRAFT}; use {@link #changeStatus} to move them on. */
    @Transactional
    public DocumentResponse create(CreateDocumentRequest request) {
        DocumentEntity document = new DocumentEntity(
                request.title(), request.description(), request.ownerId(), DocumentStatus.DRAFT);
        document.setTags(request.tags());

        if (request.file() != null) {
            document.addFile(newFile(request.file(), FIRST_VERSION, request.ownerId()));
        }

        // Flush so the response carries the generated id and timestamps.
        DocumentEntity saved = documentRepository.saveAndFlush(document);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("title", saved.getTitle());
        metadata.put("ownerId", saved.getOwnerId());
        metadata.put("status", saved.getStatus());
        metadata.put("tags", Set.copyOf(saved.getTags()));
        saved.latestFile().ifPresent(file -> metadata.put("initialVersion", file.getVersionNumber()));
        auditService.record(AuditAction.DOCUMENT_CREATED, saved.getId(), metadata);

        return DocumentResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public DocumentResponse findById(UUID id) {
        return DocumentResponse.from(getOrThrow(id));
    }

    /** An unmatched tag or period yields an empty page; only a self-contradictory period is rejected. */
    @Transactional(readOnly = true)
    public Page<DocumentResponse> search(DocumentFilter filter, Pageable pageable) {
        if (filter.createdFrom() != null
                && filter.createdTo() != null
                && filter.createdFrom().isAfter(filter.createdTo())) {
            throw new InvalidFilterException("createdFrom must not be after createdTo");
        }

        return documentRepository
                .findAll(DocumentSpecification.filter(filter), pageable)
                .map(DocumentResponse::from);
    }

    @Transactional
    public DocumentResponse update(UUID id, UpdateDocumentRequest request) {
        DocumentEntity document = getOrThrow(id);

        // Snapshot before mutating so the trail can show what actually changed.
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        recordChange(before, after, "title", document.getTitle(), request.title());
        recordChange(before, after, "description", document.getDescription(), request.description());
        // Copy: getTags() is a live view that setTags would rewrite under us.
        recordChange(before, after, "tags", Set.copyOf(document.getTags()), normalisedTags(request.tags()));

        document.setTitle(request.title());
        document.setDescription(request.description());
        document.setTags(request.tags());
        // Flush so the response carries the refreshed updated_at.
        DocumentEntity saved = documentRepository.saveAndFlush(document);
        auditService.record(AuditAction.DOCUMENT_UPDATED, id, Map.of("before", before, "after", after));

        return DocumentResponse.from(saved);
    }

    @Transactional
    public DocumentResponse changeStatus(UUID id, DocumentStatus target) {
        DocumentEntity document = getOrThrow(id);
        DocumentStatus current = document.getStatus();

        if (current == target) {
            return DocumentResponse.from(document);
        }
        if (!ALLOWED_TRANSITIONS.get(current).contains(target)) {
            throw new InvalidStatusTransitionException(current, target);
        }

        document.setStatus(target);
        DocumentEntity saved = documentRepository.saveAndFlush(document);
        auditService.record(statusAction(target), id, Map.of("from", current, "to", target));

        return DocumentResponse.from(saved);
    }

    /**
     * Registers the next file version for a document. The version number is derived from the highest
     * one already stored, so callers never choose it; concurrent uploads race on
     * {@code uq_document_version} and the loser is reported as a conflict.
     */
    @Transactional
    public DocumentFileResponse addFile(UUID documentId, DocumentFileRequest request) {
        DocumentEntity document = getOrThrow(documentId);
        int nextVersion = documentFileRepository.findLatestVersionNumber(documentId) + 1;

        DocumentFileEntity file = newFile(request, nextVersion, document.getOwnerId());
        document.addFile(file);
        documentRepository.saveAndFlush(document);
        auditService.record(
                AuditAction.FILE_UPLOADED,
                documentId,
                Map.of(
                        "versionNumber", file.getVersionNumber(),
                        "fileKey", file.getFileKey(),
                        "checksum", file.getChecksum(),
                        "uploadedBy", file.getUploadedBy()));

        return DocumentFileResponse.from(file);
    }

    /** Newest version first. Fails with 404 when the document itself does not exist. */
    @Transactional(readOnly = true)
    public List<DocumentFileResponse> listFiles(UUID documentId) {
        requireDocument(documentId);
        return documentFileRepository.findByDocumentIdOrderByVersionNumberDesc(documentId).stream()
                .map(DocumentFileResponse::from)
                .toList();
    }

    /**
     * Returns one version's storage reference. The bytes live in the storage backend behind
     * {@code fileKey}; this service hands out the reference and its checksum.
     */
    @Transactional // not read-only: fetching a version is what records the download
    public DocumentFileResponse getFile(UUID documentId, int versionNumber) {
        requireDocument(documentId);
        DocumentFileResponse file = documentFileRepository
                .findByDocumentIdAndVersionNumber(documentId, versionNumber)
                .map(DocumentFileResponse::from)
                .orElseThrow(() -> new FileVersionNotFoundException(documentId, versionNumber));

        auditService.record(
                AuditAction.FILE_DOWNLOADED,
                documentId,
                Map.of("versionNumber", file.versionNumber(), "fileKey", file.fileKey()));
        return file;
    }

    /** Cascades to the document's files, both in the entity graph and via the FK's ON DELETE CASCADE. */
    @Transactional
    public void delete(UUID id) {
        documentRepository.delete(getOrThrow(id));
    }

    /** Adds an entry to the before/after pair only when the value actually changed. */
    private static void recordChange(
            Map<String, Object> before, Map<String, Object> after, String field, Object oldValue, Object newValue) {

        if (!Objects.equals(oldValue, newValue)) {
            before.put(field, oldValue);
            after.put(field, newValue);
        }
    }

    /** Mirrors {@link DocumentEntity#setTags} so the comparison sees the stored form. */
    private static Set<String> normalisedTags(Collection<String> tags) {
        if (tags == null) {
            return Set.of();
        }
        return tags.stream()
                .map(DocumentEntity::normaliseTag)
                .filter(tag -> !tag.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static AuditAction statusAction(DocumentStatus target) {
        return switch (target) {
            case PUBLISHED -> AuditAction.DOCUMENT_PUBLISHED;
            case ARCHIVED -> AuditAction.DOCUMENT_ARCHIVED;
            case DRAFT -> AuditAction.DOCUMENT_UPDATED; // unreachable: nothing transitions back to draft
        };
    }

    private static DocumentFileEntity newFile(DocumentFileRequest request, int version, UUID documentOwner) {
        UUID uploadedBy = request.uploadedBy() == null ? documentOwner : request.uploadedBy();
        return new DocumentFileEntity(
                version, request.fileKey(), request.checksum().toLowerCase(Locale.ROOT), uploadedBy);
    }

    private DocumentEntity getOrThrow(UUID id) {
        return documentRepository.findById(id).orElseThrow(() -> new DocumentNotFoundException(id));
    }

    private void requireDocument(UUID id) {
        if (!documentRepository.existsById(id)) {
            throw new DocumentNotFoundException(id);
        }
    }
}
