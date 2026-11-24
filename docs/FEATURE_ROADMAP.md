# TRUSTLENS feature roadmap

Status is based on the current repository. "Implemented" means present in code; it does not mean independently security-certified.

## Implemented foundation

- [x] Database-backed authentication with BCrypt, server-side sessions, logout invalidation, session ID rotation and CSRF.
- [x] Database-backed roles and server-derived identity.
- [x] Deny-by-default checks for account state, document state, owner, PUBLIC READ, active READ/DOWNLOAD grant, grant expiry and same-department SECURITY_ADMIN READ.
- [x] Document visibility filtering, including active grants and same-department security administrators.
- [x] Persistent ALLOW/DENY decisions with reason code and evidence snapshot.
- [x] SECURITY_ADMIN grant creation, expiry validation, listing and revocation.
- [x] Private filesystem storage with generated keys, path validation, 10 MB cap, allowlisted media types, basic signature checks and SHA-256 metadata.
- [x] Authenticated multipart upload and per-request protected download gateway; no public storage URL.
- [x] Hash-linked audit events for access decisions, grant creation/revocation and document uploads.
- [x] React UI for login, upload, document access/download, grant administration, decisions, audit history and synthetic policy sandbox.
- [x] Docker development/production topology and CI build/test/audit/container jobs.

## Partial / requires hardening

- [ ] Full ABAC policy model: current evaluator is deterministic and fixed, not a general rule authoring engine.
- [ ] File scanning: MIME allowlist and basic signatures are not antivirus/malware scanning.
- [ ] Audit integrity: database hash chaining exists; independent signed checkpoints, verifier tooling and external key custody do not.
- [ ] Session deployment: secure cookie and CORS settings must be configured for actual HTTPS hosting origins.
- [ ] Operational readiness: user provisioning, rate limiting, monitoring, alerting, backups, restore drills and security review remain.

## Remaining roadmap

### P2 — policy sandbox and configurable ABAC
- [ ] Define a typed policy model for roles, departments, classification, actions, grant expiry and resource state.
- [ ] Simulate proposed policy against a read-only synthetic or approved snapshot.
- [ ] Show ALLOW/DENY changes, affected identities/resources, explanations and impact graph.
- [ ] Ensure simulation cannot mutate live policy or grants.

### P3 — immutable policy lifecycle
- [ ] Draft, review, approve and publish policy versions.
- [ ] Make published versions immutable at application and database layers.
- [ ] Record author, approver, publication time and policy hash.
- [ ] Associate every decision with the exact policy version used.
- [ ] Roll back by publishing a new version, never rewriting history.

### P4 — Policy Impact Verification (PIV)
- [ ] Persist simulation snapshot and predicted decision set.
- [ ] Observe decisions made after publication.
- [ ] Compare predicted versus observed outcomes.
- [ ] Distinguish matched, unexpected and not-yet-observed cases.

### P5 — historical replay and audit assurance
- [ ] Reconstruct historical decisions from immutable policy and evidence snapshots.
- [ ] Compare historical and current policy outcomes.
- [ ] Add audit-chain verifier and tamper-detection tests.
- [ ] Add independently stored signed checkpoints with protected signing-key custody.

### P6 — production hardening
- [ ] Add malware scanning and quarantine.
- [ ] Add rate limiting, account provisioning/recovery and operational monitoring.
- [ ] Add backup/restore automation and retention policy.
- [ ] Add end-to-end adversarial tests for BOLA/IDOR, privilege escalation, CSRF, session handling, upload validation, direct download retry and revocation.
- [ ] Complete accessibility, responsive UI and independent security review.

## Explicit limitations

- Revocation blocks subsequent application-mediated requests; it cannot retract bytes already downloaded or copied.
- A hash chain alone is not absolute immutability.
- Anomaly detection must not silently grant access or replace authorization.
- No real campus confidential records should be used in the hackathon demo.
