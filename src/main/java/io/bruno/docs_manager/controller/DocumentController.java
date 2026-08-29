package io.bruno.docs_manager.controller;

import io.bruno.docs_manager.dto.ChangeStatusRequest;
import io.bruno.docs_manager.dto.CreateDocumentRequest;
import io.bruno.docs_manager.dto.DocumentResponse;
import io.bruno.docs_manager.dto.UpdateDocumentRequest;
import io.bruno.docs_manager.entity.DocumentStatus;
import io.bruno.docs_manager.service.DocumentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping
    public ResponseEntity<DocumentResponse> create(
            @Valid @RequestBody CreateDocumentRequest request, UriComponentsBuilder uriBuilder) {
        DocumentResponse document = documentService.create(request);
        URI location = uriBuilder.path("api/documents/{id}").build(document.id());
        return ResponseEntity.created(location).body(document);
    }

    @GetMapping("/{id}")
    public DocumentResponse findById(@PathVariable UUID id) {
        return documentService.findById(id);
    }

    @GetMapping
    public PagedModel<DocumentResponse> list(
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) DocumentStatus status,
            @RequestParam(required = false) String title,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return new PagedModel<>(documentService.search(ownerId, status, title, pageable));
    }

    @PutMapping("/{id}")
    public DocumentResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateDocumentRequest request) {
        return documentService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public DocumentResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody ChangeStatusRequest request) {
        return documentService.changeStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        documentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
