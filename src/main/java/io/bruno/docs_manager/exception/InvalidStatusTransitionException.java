package io.bruno.docs_manager.exception;

import io.bruno.docs_manager.entity.DocumentStatus;

public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(DocumentStatus from, DocumentStatus to) {
        super("Cannot change status from %s to %s".formatted(from, to));
    }
}
