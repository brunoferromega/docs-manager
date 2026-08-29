package io.bruno.docs_manager.repository;

import io.bruno.docs_manager.dto.DocumentFilter;
import io.bruno.docs_manager.entity.DocumentEntity;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public final class DocumentSpecification {

    private static final char LIKE_ESCAPE = '\\';

    private DocumentSpecification() {}

    /** Every criterion is optional; a null/blank one simply drops its predicate. */
    public static Specification<DocumentEntity> filter(DocumentFilter filter) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.ownerId() != null) {
                predicates.add(builder.equal(root.get("ownerId"), filter.ownerId()));
            }
            if (filter.status() != null) {
                predicates.add(builder.equal(root.get("status"), filter.status()));
            }
            if (StringUtils.hasText(filter.title())) {
                String pattern = "%" + escapeLike(filter.title().toLowerCase()) + "%";
                predicates.add(builder.like(builder.lower(root.get("title")), pattern, LIKE_ESCAPE));
            }
            if (StringUtils.hasText(filter.tag())) {
                // (document_id, tag) is the collection table's PK, so this join cannot duplicate a document.
                Join<DocumentEntity, String> tags = root.join("tags");
                predicates.add(builder.equal(tags, DocumentEntity.normaliseTag(filter.tag())));
            }
            if (filter.createdFrom() != null) {
                predicates.add(builder.greaterThanOrEqualTo(
                        root.<OffsetDateTime>get("createdAt"), filter.createdFrom()));
            }
            if (filter.createdTo() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.<OffsetDateTime>get("createdAt"), filter.createdTo()));
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
