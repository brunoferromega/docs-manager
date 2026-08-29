-- Free-form labels attached to a document, stored normalised (trimmed + lower case).
CREATE TABLE document_tags
(
    document_id UUID        NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    tag         VARCHAR(64) NOT NULL,

    CONSTRAINT pk_document_tags PRIMARY KEY (document_id, tag)
);

CREATE INDEX idx_document_tags_tag ON document_tags (tag);

-- Supports the created_at range filter and the default listing order.
CREATE INDEX idx_documents_created_at ON documents (created_at);
