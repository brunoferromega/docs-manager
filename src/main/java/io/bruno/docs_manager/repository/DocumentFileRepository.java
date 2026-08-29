package io.bruno.docs_manager.repository;

import io.bruno.docs_manager.entity.DocumentFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentFileRepository extends JpaRepository<DocumentFileEntity, UUID> {

    List<DocumentFileEntity> findByDocumentIdOrderByVersionNumberDesc(UUID documentId);

    Optional<DocumentFileEntity> findByDocumentIdAndVersionNumber(UUID documentId, Integer versionNumber);

    Optional<DocumentFileEntity> findFirstByDocumentIdOrderByVersionNumberDesc(UUID documentId);

    @Query("select coalesce(max(f.versionNumber), 0) from DocumentFileEntity f where f.document.id = :documentId")
    int findLatestVersionNumber(@Param("documentId") UUID documentId);
}
