# Database

PostgreSQL 16 is used for local development. Schema evolution belongs in backend Flyway migrations under `backend/src/main/resources/db/migration`.

Initial domain tables planned: departments, roles, users, user_roles, documents, access_grants, policies, policy_versions, access_decisions, policy_changes, audit_events, anomaly_events.

Use UUID identifiers, UTC timestamps (`timestamptz`), constraints for valid states, and indexes for authorization lookup paths. Never store plaintext passwords; delegate password hashing to Spring Security's password encoder. Production secrets must not use the local compose defaults.
