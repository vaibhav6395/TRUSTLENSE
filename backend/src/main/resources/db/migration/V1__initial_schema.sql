CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    username VARCHAR(80) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    department_code VARCHAR(40),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE app_roles (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(200)
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES app_users(id),
    role_id UUID NOT NULL REFERENCES app_roles(id),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE documents (
    id UUID PRIMARY KEY,
    title VARCHAR(240) NOT NULL,
    owner_id UUID NOT NULL REFERENCES app_users(id),
    department_code VARCHAR(40),
    classification VARCHAR(30) NOT NULL CHECK (classification IN ('PUBLIC','INTERNAL','CONFIDENTIAL','RESTRICTED')),
    storage_key VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE','RETIRED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE access_grants (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES documents(id),
    subject_user_id UUID NOT NULL REFERENCES app_users(id),
    permission VARCHAR(20) NOT NULL CHECK (permission IN ('READ','DOWNLOAD')),
    granted_by UUID NOT NULL REFERENCES app_users(id),
    expires_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    revoked_by UUID REFERENCES app_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_grants_subject_document ON access_grants(subject_user_id, document_id);
CREATE INDEX idx_grants_expiry ON access_grants(expires_at) WHERE revoked_at IS NULL;

CREATE TABLE policy_versions (
    id UUID PRIMARY KEY,
    policy_key VARCHAR(100) NOT NULL,
    version_number INTEGER NOT NULL CHECK (version_number > 0),
    rules JSONB NOT NULL,
    published_by UUID REFERENCES app_users(id),
    published_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(policy_key, version_number)
);

CREATE TABLE access_decisions (
    id UUID PRIMARY KEY,
    actor_user_id UUID REFERENCES app_users(id),
    document_id UUID REFERENCES documents(id),
    action VARCHAR(20) NOT NULL,
    outcome VARCHAR(10) NOT NULL CHECK (outcome IN ('ALLOW','DENY')),
    reason_code VARCHAR(80) NOT NULL,
    explanation TEXT NOT NULL,
    policy_version_id UUID REFERENCES policy_versions(id),
    evidence_snapshot JSONB NOT NULL,
    decided_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_decisions_document_time ON access_decisions(document_id, decided_at DESC);

CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    sequence_no BIGINT NOT NULL UNIQUE,
    actor_user_id UUID REFERENCES app_users(id),
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    previous_hash CHAR(64),
    event_hash CHAR(64) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_events_time ON audit_events(occurred_at DESC);
