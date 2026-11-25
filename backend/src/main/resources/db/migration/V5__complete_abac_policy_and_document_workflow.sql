ALTER TABLE app_users
    ADD COLUMN institutional_email VARCHAR(254),
    ADD COLUMN faculty_identifier VARCHAR(80),
    ADD COLUMN clearance_level SMALLINT NOT NULL DEFAULT 1 CHECK (clearance_level BETWEEN 0 AND 5),
    ADD COLUMN attributes JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE documents
    ADD COLUMN description TEXT NOT NULL DEFAULT '',
    ADD COLUMN tags JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN required_clearance SMALLINT NOT NULL DEFAULT 0 CHECK (required_clearance BETWEEN 0 AND 5),
    ADD COLUMN retention_until TIMESTAMPTZ,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE access_grants
    ADD COLUMN starts_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN purpose VARCHAR(500) NOT NULL DEFAULT '',
    ADD COLUMN max_downloads INTEGER CHECK (max_downloads IS NULL OR max_downloads > 0),
    ADD COLUMN download_count INTEGER NOT NULL DEFAULT 0 CHECK (download_count >= 0);

ALTER TABLE policy_versions
    ALTER COLUMN published_at DROP NOT NULL,
    ADD COLUMN status VARCHAR(24) NOT NULL DEFAULT 'PUBLISHED'
      CHECK (status IN ('DRAFT','PENDING_APPROVAL','PUBLISHED','REJECTED')),
    ADD COLUMN created_by UUID REFERENCES app_users(id),
    ADD COLUMN approved_by UUID REFERENCES app_users(id),
    ADD COLUMN review_note VARCHAR(1000),
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE UNIQUE INDEX idx_policy_one_published_per_key_version
    ON policy_versions(policy_key, version_number);
CREATE INDEX idx_policy_published_latest
    ON policy_versions(policy_key, version_number DESC)
    WHERE status='PUBLISHED';

CREATE TABLE policy_change_events (
    id UUID PRIMARY KEY,
    policy_version_id UUID NOT NULL REFERENCES policy_versions(id),
    actor_user_id UUID NOT NULL REFERENCES app_users(id),
    event_type VARCHAR(32) NOT NULL CHECK (event_type IN ('DRAFT_CREATED','SUBMITTED','APPROVED','REJECTED','PUBLISHED')),
    note VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE document_shares (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    created_by UUID NOT NULL REFERENCES app_users(id),
    share_token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_document_shares_document ON document_shares(document_id, expires_at)
    WHERE revoked_at IS NULL;

CREATE TABLE document_download_events (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES documents(id),
    user_id UUID NOT NULL REFERENCES app_users(id),
    grant_id UUID REFERENCES access_grants(id),
    downloaded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_download_events_doc_user ON document_download_events(document_id,user_id,downloaded_at DESC);

