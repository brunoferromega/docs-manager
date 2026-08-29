package io.bruno.docs_manager.exception;

import java.util.UUID;

public class FileVersionNotFoundException extends RuntimeException {

    public FileVersionNotFoundException(UUID documentId, int versionNumber) {
        super("Document %s has no file version %d".formatted(documentId, versionNumber));
    }
}
