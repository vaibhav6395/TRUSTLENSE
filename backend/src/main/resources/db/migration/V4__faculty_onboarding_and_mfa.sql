ALTER TABLE app_users
    ADD COLUMN mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN mfa_secret_encrypted TEXT,
    ADD COLUMN mfa_enrolled_at TIMESTAMPTZ;

CREATE TABLE faculty_signup_requests (
    id UUID PRIMARY KEY,
    username VARCHAR(80) NOT NULL UNIQUE,
    institutional_email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    department_code VARCHAR(40) NOT NULL,
    faculty_identifier VARCHAR(80) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    review_note VARCHAR(1000),
    reviewed_by UUID REFERENCES app_users(id),
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_faculty_signup_status_created
    ON faculty_signup_requests(status, created_at DESC);

CREATE TABLE auth_mfa_challenges (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL UNIQUE,
    purpose VARCHAR(16) NOT NULL CHECK (purpose IN ('ENROLL','LOGIN')),
    pending_secret_encrypted TEXT,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_auth_mfa_challenge_expiry ON auth_mfa_challenges(expires_at);
