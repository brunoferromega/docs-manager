package io.bruno.docs_manager.service;

import io.bruno.docs_manager.entity.AuditAction;
import io.bruno.docs_manager.entity.AuditEventEntity;
import io.bruno.docs_manager.repository.AuditEventRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Writes the audit trail. Records join the caller's transaction, so an event is only ever visible
 * if the change it describes was actually committed.
 */
@Service
public class AuditService {

    private static final String UNKNOWN_USER = "anonymous";

    private final AuditEventRepository auditEventRepository;

    public AuditService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional
    public void record(AuditAction action, UUID documentId, Map<String, Object> metadata) {
        Actor actor = currentActor();
        auditEventRepository.save(
                new AuditEventEntity(action, documentId, actor.userId(), actor.username(), metadata));
    }

    /** Reads the acting user off the bearer token; falls back to anonymous outside a request. */
    private Actor currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            String uid = jwt.getClaimAsString("uid");
            return new Actor(uid == null ? null : UUID.fromString(uid), jwt.getSubject());
        }
        if (authentication != null && authentication.isAuthenticated()) {
            return new Actor(null, authentication.getName());
        }
        return new Actor(null, UNKNOWN_USER);
    }

    private record Actor(UUID userId, String username) {}
}
