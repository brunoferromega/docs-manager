package io.bruno.docs_manager.repository;

import io.bruno.docs_manager.entity.DocumentEntity;
import io.bruno.docs_manager.entity.DocumentStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DocumentSpecification {

    private static final char LIKE_ESCAPE = '\\';

    private DocumentSpecification() {}

    /** Every argument is optional; a null/blank one simply drops its predicate. */
    public static Specification<DocumentEntity> filter(UUID ownerId, DocumentStatus status, String title) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (ownerId != null) {
                predicates.add(builder.equal(root.get("ownerId"), ownerId));
            }
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (StringUtils.hasText(title)) {
                String pattern = "%" + escapeLike(title.toLowerCase()) + "%";
                predicates.add(builder.like(builder.lower(root.get("title")), pattern, LIKE_ESCAPE));
            }

            // An empty array yields a conjunction, i.e. "no filter".
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** Keeps user-supplied wildcards from widening the search. */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
