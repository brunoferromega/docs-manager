-- Append-only trail of everything that happens to a document.
-- Deliberately no foreign key to documents: the trail must outlive the rows it describes.
CREATE TABLE audit_events
(
    id          UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    occurred_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    action      VARCHAR(32)  NOT NULL,
    document_id UUID,
    user_id     UUID,
    username    VARCHAR(64)  NOT NULL,
    metadata    JSONB        NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX idx_audit_events_document_id ON audit_events (document_id);
CREATE INDEX idx_audit_events_occurred_at ON audit_events (occurred_at DESC);
