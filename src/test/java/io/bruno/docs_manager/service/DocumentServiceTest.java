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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    private static final UUID DOCUMENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OWNER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String CHECKSUM = "a".repeat(64);

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentFileRepository documentFileRepository;

    @Mock
    private AuditService auditService;

    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        documentService = new DocumentService(documentRepository, documentFileRepository, auditService);
    }

    @Test
    @DisplayName("creates a draft with normalised tags and audits the creation")
    void createStartsAsDraftWithNormalisedTags() {
        when(documentRepository.saveAndFlush(any(DocumentEntity.class))).thenAnswer(invocation -> {
            DocumentEntity document = invocation.getArgument(0);
            ReflectionTestUtils.setField(document, "id", DOCUMENT_ID);
            return document;
        });

        DocumentResponse response = documentService.create(new CreateDocumentRequest(
                "Quarterly report", "numbers", Set.of("Finance", "  URGENT  "), OWNER_ID, null));

        assertThat(response.status()).isEqualTo(DocumentStatus.DRAFT);
        assertThat(response.tags()).containsExactlyInAnyOrder("finance", "urgent");
        assertThat(response.latestFile()).isNull();

        ArgumentCaptor<Map<String, Object>> metadata = captor();
        verify(auditService).record(eq(AuditAction.DOCUMENT_CREATED), eq(DOCUMENT_ID), metadata.capture());
        assertThat(metadata.getValue())
                .containsEntry("title", "Quarterly report")
                .containsEntry("status", DocumentStatus.DRAFT);
    }

    @Test
    @DisplayName("stores a file supplied at creation as version 1")
    void createStoresSuppliedFileAsFirstVersion() {
        when(documentRepository.saveAndFlush(any(DocumentEntity.class))).thenAnswer(invocation -> {
            DocumentEntity document = invocation.getArgument(0);
            ReflectionTestUtils.setField(document, "id", DOCUMENT_ID);
            return document;
        });

        DocumentResponse response = documentService.create(new CreateDocumentRequest(
                "With file",
                null,
                null,
                OWNER_ID,
                new DocumentFileRequest("s3://bucket/key", CHECKSUM.toUpperCase(), null)));

        assertThat(response.latestFile()).isNotNull();
        assertThat(response.latestFile().versionNumber()).isEqualTo(1);
        // Checksums are stored lower-cased, and uploadedBy falls back to the owner.
        assertThat(response.latestFile().checksum()).isEqualTo(CHECKSUM);
        assertThat(response.latestFile().uploadedBy()).isEqualTo(OWNER_ID);
    }

    @Test
    @DisplayName("audits only the fields an update actually changed")
    void updateAuditsOnlyChangedFields() {
        DocumentEntity document = existingDocument(DocumentStatus.DRAFT);
        document.setTags(List.of("keep"));
        when(documentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.of(document));
        when(documentRepository.saveAndFlush(document)).thenReturn(document);

        documentService.update(DOCUMENT_ID, new UpdateDocumentRequest("New title", "description", Set.of("KEEP")));

        ArgumentCaptor<Map<String, Object>> metadata = captor();
        verify(auditService).record(eq(AuditAction.DOCUMENT_UPDATED), eq(DOCUMENT_ID), metadata.capture());

        @SuppressWarnings("unchecked")
        Map<String, Object> before = (Map<String, Object>) metadata.getValue().get("before");
        @SuppressWarnings("unchecked")
        Map<String, Object> after = (Map<String, Object>) metadata.getValue().get("after");

        assertThat(before).containsEntry("title", "Original title");
        assertThat(after).containsEntry("title", "New title");
        // "keep" and "KEEP" normalise to the same tag, so tags are not reported as changed.
        assertThat(before).doesNotContainKey("tags");
        assertThat(after).doesNotContainKey("tags");
    }

    @Test
    @DisplayName("publishing a draft records the transition")
    void publishingADraftRecordsTheTransition() {
        DocumentEntity document = existingDocument(DocumentStatus.DRAFT);
        when(documentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.of(document));
        when(documentRepository.saveAndFlush(document)).thenReturn(document);

        DocumentResponse response = documentService.changeStatus(DOCUMENT_ID, DocumentStatus.PUBLISHED);

        assertThat(response.status()).isEqualTo(DocumentStatus.PUBLISHED);
        verify(auditService)
                .record(
                        AuditAction.DOCUMENT_PUBLISHED,
                        DOCUMENT_ID,
                        Map.of("from", DocumentStatus.DRAFT, "to", DocumentStatus.PUBLISHED));
    }

    @Test
    @DisplayName("rejects an illegal status transition without saving or auditing")
    void rejectsIllegalStatusTransition() {
        DocumentEntity document = existingDocument(DocumentStatus.PUBLISHED);
        when(documentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> documentService.changeStatus(DOCUMENT_ID, DocumentStatus.DRAFT))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("PUBLISHED")
                .hasMessageContaining("DRAFT");

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.PUBLISHED);
        verify(documentRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("re-applying the current status changes nothing")
    void reapplyingCurrentStatusIsANoop() {
        DocumentEntity document = existingDocument(DocumentStatus.PUBLISHED);
        when(documentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.of(document));

        DocumentResponse response = documentService.changeStatus(DOCUMENT_ID, DocumentStatus.PUBLISHED);

        assertThat(response.status()).isEqualTo(DocumentStatus.PUBLISHED);
        verify(documentRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("assigns the next version number when uploading a file")
    void addFileAssignsNextVersionNumber() {
        DocumentEntity document = existingDocument(DocumentStatus.DRAFT);
        when(documentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.of(document));
        when(documentFileRepository.findLatestVersionNumber(DOCUMENT_ID)).thenReturn(4);
        when(documentRepository.saveAndFlush(document)).thenReturn(document);

        DocumentFileResponse response =
                documentService.addFile(DOCUMENT_ID, new DocumentFileRequest("s3://bucket/v5", CHECKSUM, null));

        assertThat(response.versionNumber()).isEqualTo(5);
        verify(auditService).record(eq(AuditAction.FILE_UPLOADED), eq(DOCUMENT_ID), any());
    }

    @Test
    @DisplayName("fetching a version records a download")
    void getFileRecordsADownload() {
        DocumentEntity document = existingDocument(DocumentStatus.PUBLISHED);
        DocumentFileEntity file = new DocumentFileEntity(2, "s3://bucket/v2", CHECKSUM, OWNER_ID);
        document.addFile(file);

        when(documentRepository.existsById(DOCUMENT_ID)).thenReturn(true);
        when(documentFileRepository.findByDocumentIdAndVersionNumber(DOCUMENT_ID, 2)).thenReturn(Optional.of(file));

        DocumentFileResponse response = documentService.getFile(DOCUMENT_ID, 2);

        assertThat(response.versionNumber()).isEqualTo(2);
        verify(auditService)
                .record(
                        AuditAction.FILE_DOWNLOADED,
                        DOCUMENT_ID,
                        Map.of("versionNumber", 2, "fileKey", "s3://bucket/v2"));
    }

    @Test
    @DisplayName("reports a missing file version as not found")
    void missingFileVersionIsNotFound() {
        when(documentRepository.existsById(DOCUMENT_ID)).thenReturn(true);
        when(documentFileRepository.findByDocumentIdAndVersionNumber(DOCUMENT_ID, 99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.getFile(DOCUMENT_ID, 99))
                .isInstanceOf(FileVersionNotFoundException.class)
                .hasMessageContaining("99");

        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("reports an unknown document as not found")
    void unknownDocumentIsNotFound() {
        when(documentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.findById(DOCUMENT_ID))
                .isInstanceOf(DocumentNotFoundException.class)
                .hasMessageContaining(DOCUMENT_ID.toString());
    }

    @Test
    @DisplayName("rejects a period whose start is after its end")
    void rejectsInvertedPeriodFilter() {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-29T12:00:00Z");
        DocumentFilter filter = new DocumentFilter(null, null, null, null, now, now.minusDays(1));

        assertThatThrownBy(() -> documentService.search(filter, PageRequest.of(0, 20)))
                .isInstanceOf(InvalidFilterException.class)
                .hasMessageContaining("createdFrom");

        verifyNoInteractions(documentRepository);
    }

    private static DocumentEntity existingDocument(DocumentStatus status) {
        DocumentEntity document = new DocumentEntity("Original title", "description", OWNER_ID, status);
        ReflectionTestUtils.setField(document, "id", DOCUMENT_ID);
        return document;
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Map<String, Object>> captor() {
        return ArgumentCaptor.forClass(Map.class);
    }
}
