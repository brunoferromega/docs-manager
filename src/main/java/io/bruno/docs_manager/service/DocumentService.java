package io.bruno.docs_manager.service;

import io.bruno.docs_manager.dto.CreateDocumentRequest;
import io.bruno.docs_manager.dto.DocumentResponse;
import io.bruno.docs_manager.dto.UpdateDocumentRequest;
import io.bruno.docs_manager.entity.DocumentEntity;
import io.bruno.docs_manager.entity.DocumentStatus;
import io.bruno.docs_manager.exception.DocumentNotFoundException;
import io.bruno.docs_manager.exception.InvalidStatusTransitionException;
import io.bruno.docs_manager.repository.DocumentRepository;
import io.bruno.docs_manager.repository.DocumentSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class DocumentService {

    private static final Map<DocumentStatus, Set<DocumentStatus>> ALLOWED_TRANSITIONS = Map.of(
            DocumentStatus.DRAFT, EnumSet.of(DocumentStatus.PUBLISHED, DocumentStatus.ARCHIVED),
            DocumentStatus.PUBLISHED, EnumSet.of(DocumentStatus.ARCHIVED),
            DocumentStatus.ARCHIVED, EnumSet.noneOf(DocumentStatus.class));

    private final DocumentRepository documentRepository;

    public DocumentService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    /** Documents always start as {@link DocumentStatus#DRAFT}; use {@link #changeStatus} to move them on. */
    @Transactional
    public DocumentResponse create(CreateDocumentRequest request) {
        DocumentEntity document = new DocumentEntity(
                request.title(), request.description(), request.ownerId(), DocumentStatus.DRAFT);
        // Flush so the response carries the generated id and timestamps.
        return DocumentResponse.from(documentRepository.saveAndFlush(document));
    }

    @Transactional(readOnly = true)
    public DocumentResponse findById(UUID id) {
        return DocumentResponse.from(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public Page<DocumentResponse> search(UUID ownerId, DocumentStatus status, String title, Pageable pageable) {
        return documentRepository
                .findAll(DocumentSpecification.filter(ownerId, status, title), pageable)
                .map(DocumentResponse::from);
    }

    @Transactional
    public DocumentResponse update(UUID id, UpdateDocumentRequest request) {
        DocumentEntity document = getOrThrow(id);
        document.setTitle(request.title());
        document.setDescription(request.description());
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

    /** Cascades to the document's files, both in the entity graph and via the FK's ON DELETE CASCADE. */
    @Transactional
    public void delete(UUID id) {
        documentRepository.delete(getOrThrow(id));
    }

    private DocumentEntity getOrThrow(UUID id) {
        return documentRepository.findById(id).orElseThrow(() -> new DocumentNotFoundException(id));
    }
}
