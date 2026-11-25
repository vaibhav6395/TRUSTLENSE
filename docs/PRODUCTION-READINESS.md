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
- Faculty signup approval workflow, production-only first administrator bootstrap, TOTP MFA before session issuance, encrypted MFA secrets, one-time recovery codes and database-backed password login throttling.
- Administrator account enable/disable controls; disabled accounts are rejected by the session refresh filter on subsequent requests.
- Live RBAC/ABAC evaluator using current roles, department, clearance, classification, tags and structured subject/resource attributes.
- Versioned policy draft/simulation/independent approval/publication, historical decision replay and bounded Policy Impact Verification.
- Owner/admin document metadata and lifecycle controls, retention expiry and retirement with grant revocation.
- Per-request scheduled, expiring, revocable grant checks and download quotas.
- Private server-side file storage with generated storage keys, upload size/type/signature checks and SHA-256 metadata.
- Persistent access decisions with evidence snapshots and policy version references, plus hash-linked identity, policy, sharing, document and download audit events.
- Flyway-managed schema and CI checks for builds, tests, dependency audit, image builds and a revocation smoke test.

## Phase 1 — Hackathon runnable baseline and production safety boundary

- [x] Add a dedicated Spring prod profile with demo data disabled, secure session cookie defaults, forwarded-header handling and sanitized API error responses.
- [x] Exclude the synthetic demo initializer whenever the prod profile is active.
- [x] Make the production Compose stack activate the prod profile explicitly.
- [x] Run backend tests, frontend tests/build, dependency audit, secret scan and both container image builds in GitHub Actions.
- [x] Run the end-to-end smoke test proving revoked READ and DOWNLOAD grants block a still-authenticated session and reused file URL.
- [ ] Add a dedicated integration test proving demo users are never seeded under the production profile.
- [ ] Add end-to-end tests for pending faculty login denial, admin approval, MFA enrollment/recovery and account disablement.
- [ ] Add browser/database integration tests for policy approval separation, custom attribute matching, download quotas, document retention and historical replay/PIV.
- [ ] Validate production Compose configuration on the target host and verify the HTTPS reverse proxy, persistent volumes and backup restore.

Latest recorded CI run for the production branch completed successfully across all five jobs: frontend, backend, secret-scan, containers and zero-trust-smoke. This validates the CI pipeline, not an independently operated deployment.

## Phase 2 — Institutional identity and lifecycle

- Replace demo-only account creation with controlled institutional provisioning or integration with the institution's identity provider (OIDC/SAML).
- Define joiner/mover/leaver workflows, account disablement, department changes and role approval.
- Add MFA through the chosen identity provider and document recovery procedures.
- Define least-privilege administrative roles and separate platform administration from security policy administration.

## Phase 3 — Authorization and policy governance

- [x] Specify a constrained versioned JSON policy model, supported RBAC/ABAC attributes, priority conflict resolution and default-deny semantics.
- [x] Add policy draft, server-side simulation, independent review/approval, publication and immutable version history.
- [x] Add historical decision replay and bounded Policy Impact Verification.
- [x] Add decision explainability with policy version and trusted evidence snapshots.
- [ ] Complete an exhaustive object-level authorization matrix and browser/database integration test suite.

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
