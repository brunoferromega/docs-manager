CREATE TYPE document_status AS ENUM ('DRAFT', 'PUBLISHED', 'ARCHIVED');

-- Logical document
CREATE TABLE documents
(
    id          UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    title       VARCHAR(255)    NOT NULL,
    description TEXT,
    owner_id    UUID            NOT NULL,
    status      document_status NOT NULL DEFAULT 'DRAFT',
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_documents_owner_id ON documents (owner_id);

-- File versions (each upload = new row)
CREATE TABLE document_files
(
    id             UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    document_id    UUID         NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    version_number INTEGER      NOT NULL,
    file_key       VARCHAR(512) NOT NULL,
    checksum       CHAR(64)     NOT NULL,
    uploaded_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    uploaded_by    UUID         NOT NULL,

    CONSTRAINT uq_document_version UNIQUE (document_id, version_number)
);

CREATE INDEX idx_document_files_document_id ON document_files (document_id);