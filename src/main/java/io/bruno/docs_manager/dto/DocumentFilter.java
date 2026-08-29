package io.bruno.docs_manager.dto;

import io.bruno.docs_manager.entity.DocumentStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Listing criteria. Every field is optional and a null/blank one drops its predicate, so an unknown
 * tag or an empty period simply yields an empty page rather than an error.
 *
 * @param createdFrom lower bound on {@code created_at}, inclusive
 * @param createdTo   upper bound on {@code created_at}, inclusive
 */
public record DocumentFilter(
        UUID ownerId,
        DocumentStatus status,
        String title,
        String tag,
        OffsetDateTime createdFrom,
        OffsetDateTime createdTo) {}
