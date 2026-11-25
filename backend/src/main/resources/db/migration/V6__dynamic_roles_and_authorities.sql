ALTER TABLE app_roles
    ADD COLUMN is_system BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE app_roles SET is_system=TRUE
WHERE code IN ('FACULTY','STUDENT','STAFF','SECURITY_ADMIN');

CREATE TABLE role_authorities (
    role_id UUID NOT NULL REFERENCES app_roles(id) ON DELETE CASCADE,
    authority_code VARCHAR(64) NOT NULL,
    PRIMARY KEY(role_id, authority_code)
);

CREATE INDEX idx_role_authorities_code ON role_authorities(authority_code);

INSERT INTO role_authorities(role_id, authority_code)
SELECT id, permission
FROM app_roles
CROSS JOIN (VALUES
    ('PERM_USER_MANAGE'),
    ('PERM_ROLE_MANAGE'),
    ('PERM_POLICY_MANAGE'),
    ('PERM_AUDIT_READ')
) AS permissions(permission)
WHERE code='SECURITY_ADMIN'
ON CONFLICT DO NOTHING;
