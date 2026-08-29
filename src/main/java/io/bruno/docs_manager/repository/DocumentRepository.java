package io.bruno.docs_manager.repository;

import io.bruno.docs_manager.entity.DocumentEntity;
import io.bruno.docs_manager.entity.DocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface DocumentRepository
        extends JpaRepository<DocumentEntity, UUID>, JpaSpecificationExecutor<DocumentEntity> {

    Page<DocumentEntity> findByOwnerId(UUID ownerId, Pageable pageable);

    Page<DocumentEntity> findByOwnerIdAndStatus(UUID ownerId, DocumentStatus status, Pageable pageable);

    boolean existsByIdAndOwnerId(UUID id, UUID ownerId);
}
