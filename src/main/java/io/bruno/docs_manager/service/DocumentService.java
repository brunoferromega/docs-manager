package io.bruno.docs_manager.service;

import io.bruno.docs_manager.dto.CreateDocumentRequest;
import io.bruno.docs_manager.dto.DocumentFileRequest;
import io.bruno.docs_manager.dto.DocumentFileResponse;
import io.bruno.docs_manager.dto.DocumentFilter;
import io.bruno.docs_manager.dto.DocumentResponse;
import io.bruno.docs_manager.dto.UpdateDocumentRequest;
import io.bruno.docs_manager.entity.DocumentEntity;
import io.bruno.docs_manager.entity.DocumentFileEntity;
import io.bruno.docs_manager.entity.DocumentStatus;
import io.bruno.docs_manager.exception.DocumentNotFoundException;
import io.bruno.docs_manager.exception.InvalidFilterException;
import io.bruno.docs_manager.exception.InvalidStatusTransitionException;
import io.bruno.docs_manager.repository.DocumentFileRepository;
import io.bruno.docs_manager.repository.DocumentRepository;
import io.bruno.docs_manager.repository.DocumentSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class DocumentService {

    private static final int FIRST_VERSION = 1;

    private static final Map<DocumentStatus, Set<DocumentStatus>> ALLOWED_TRANSITIONS = Map.of(
            DocumentStatus.DRAFT, EnumSet.of(DocumentStatus.PUBLISHED, DocumentStatus.ARCHIVED),
            DocumentStatus.PUBLISHED, EnumSet.of(DocumentStatus.ARCHIVED),
            DocumentStatus.ARCHIVED, EnumSet.noneOf(DocumentStatus.class));

    private final DocumentRepository documentRepository;
    private final DocumentFileRepository documentFileRepository;

    public DocumentService(DocumentRepository documentRepository, DocumentFileRepository documentFileRepository) {
        this.documentRepository = documentRepository;
        this.documentFileRepository = documentFileRepository;
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
        return DocumentResponse.from(documentRepository.saveAndFlush(document));
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
        document.setTitle(request.title());
        document.setDescription(request.description());
        document.setTags(request.tags());
        // Flush so the response carries the refreshed updated_at.
        return DocumentResponse.from(documentRepository.saveAndFlush(document));
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
        return DocumentResponse.from(documentRepository.saveAndFlush(document));
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

        return DocumentFileResponse.from(file);
    }

    /** Cascades to the document's files, both in the entity graph and via the FK's ON DELETE CASCADE. */
    @Transactional
    public void delete(UUID id) {
        documentRepository.delete(getOrThrow(id));
    }

    private static DocumentFileEntity newFile(DocumentFileRequest request, int version, UUID documentOwner) {
        UUID uploadedBy = request.uploadedBy() == null ? documentOwner : request.uploadedBy();
        return new DocumentFileEntity(
                version, request.fileKey(), request.checksum().toLowerCase(Locale.ROOT), uploadedBy);
    }

    private DocumentEntity getOrThrow(UUID id) {
        return documentRepository.findById(id).orElseThrow(() -> new DocumentNotFoundException(id));
    }
}
