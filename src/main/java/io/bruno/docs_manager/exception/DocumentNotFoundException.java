package io.bruno.docs_manager.exception;

import java.util.UUID;

public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(UUID id) {
        super("Document %s was not found".formatted(id));
    }
}
