# Production readiness plan

## Deployment target

- Institution-operated deployment
- Initial scale: 100–1,000 users
- Document storage: institution-controlled infrastructure
- Existing stack retained: React/Vite, Spring Boot, PostgreSQL, Flyway, private filesystem storage

## Current verified repository capabilities

These capabilities are present in the repository; they are not a claim of production certification:

- Session-based authentication with BCrypt password verification, CSRF protection and session ID rotation on login.
- Account enabled/role refresh from PostgreSQL on authenticated requests.
- Role and contextual authorization checks for document access.
- Per-request grant checks, including expiry and revocation state.
- Private server-side file storage with generated storage keys, upload size/type/signature checks and SHA-256 metadata.
- Persistent access decisions and hash-linked audit event records.
- Flyway-managed schema and CI checks for builds, tests, dependency audit, image builds and a revocation smoke test.

## Phase 1 — Hackathon runnable baseline and production safety boundary

- [x] Add a dedicated Spring prod profile with demo data disabled, secure session cookie defaults, forwarded-header handling and sanitized API error responses.
- [x] Exclude the synthetic demo initializer whenever the prod profile is active.
- [x] Make the production Compose stack activate the prod profile explicitly.
- [x] Run backend tests, frontend tests/build, dependency audit, secret scan and both container image builds in GitHub Actions.
- [x] Run the end-to-end smoke test proving revoked READ and DOWNLOAD grants block a still-authenticated session and reused file URL.
- [ ] Add a dedicated integration test proving demo users are never seeded under the production profile.
- [ ] Validate production Compose configuration on the target host and verify the HTTPS reverse proxy, persistent volumes and backup restore.

Latest recorded CI run for the production branch completed successfully across all five jobs: frontend, backend, secret-scan, containers and zero-trust-smoke. This validates the CI pipeline, not an independently operated deployment.

## Phase 2 — Institutional identity and lifecycle

- Replace demo-only account creation with controlled institutional provisioning or integration with the institution's identity provider (OIDC/SAML).
- Define joiner/mover/leaver workflows, account disablement, department changes and role approval.
- Add MFA through the chosen identity provider and document recovery procedures.
- Define least-privilege administrative roles and separate platform administration from security policy administration.

## Phase 3 — Authorization and policy governance

- Specify a versioned policy model, supported attributes, conflict resolution and default-deny semantics.
- Add policy draft, review, approval, publication, rollback and immutable version history.
- Test object-level authorization for every read, download, grant and administrative endpoint.
- Add decision explainability that records the policy version and trusted evidence used for each decision.

## Phase 4 — Institutional document protection

- Confirm storage threat model and filesystem ownership, mount options, backup and restore design.
- Add encryption at rest with institution-managed keys and a documented key rotation/recovery process.
- Add malware scanning or a quarantine workflow before documents become available.
- Enforce quotas, retention/deletion rules, content-disposition safety and audit of all file lifecycle operations.
- Test storage failure, backup restoration and authorization after restore.

## Phase 5 — Audit, monitoring and operations

- Verify the audit hash chain and define external checkpointing/retention to make privileged tampering detectable.
- Add structured security logs, metrics, alerting and privacy-aware operational dashboards.
- Add rate limiting and abuse controls for login, upload and sensitive endpoints.
- Establish patching, dependency/SBOM review, incident response, vulnerability disclosure and log retention procedures.
- Load-test the expected 100–1,000-user workload and set measurable availability/latency targets.

## Phase 6 — Release assurance

- Add automated unit, integration, browser, authorization-matrix and migration tests.
- Add threat-model review, independent penetration test and remediation tracking.
- Test disaster recovery, database backup restore, file backup restore and key recovery.
- Require a release checklist and institutional security/data-protection approval before real confidential records are used.

## Explicit non-goals for this phase

This work does not certify TRUSTLENS as secure, provide a live deployment, configure an institutional identity provider, or authorize real confidential data uploads. Keep using synthetic data until the institution's security, privacy, infrastructure and operational owners approve the deployment.
