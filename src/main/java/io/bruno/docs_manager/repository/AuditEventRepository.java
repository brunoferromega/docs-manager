package io.bruno.docs_manager.repository;

import io.bruno.docs_manager.entity.AuditEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEventEntity, UUID> {

    List<AuditEventEntity> findByDocumentIdOrderByOccurredAtDesc(UUID documentId);
}
