ALTER TABLE documents
    ADD COLUMN content_type VARCHAR(120),
    ADD COLUMN size_bytes BIGINT,
    ADD COLUMN content_sha256 CHAR(64);

CREATE INDEX idx_documents_owner_status ON documents(owner_id, status);
CREATE INDEX idx_grants_active_lookup
    ON access_grants(document_id, subject_user_id, permission, expires_at)
    WHERE revoked_at IS NULL;
