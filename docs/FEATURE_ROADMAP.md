# TRUSTLENS feature roadmap

Status is based on the current codebase. "Implemented" means present in the repository; it does not mean independently security-certified.

## Implemented foundation

- [x] Database-backed authentication with BCrypt.
- [x] Server-side sessions, logout invalidation, and session ID rotation at login.
- [x] CSRF bootstrap and cookie-based token validation.
- [x] Database-backed user roles and server-derived identity.
- [x] Deny-by-default policy evaluator.
- [x] Account enabled, document active, owner, public READ, active grant, and same-department security-admin READ checks.
- [x] Document visibility filtering.
- [x] Persistent ALLOW/DENY decisions with reason code and evidence snapshot.
- [x] Admin-only grant listing and revocation.
- [x] Hash-linked audit records for access decisions and grant revocation.
- [x] React UI for login, overview, document access, people/grants, decisions, and audit history.
- [x] Backend/frontend CI build and tests; dependency audit gate.

## Partial / requires hardening

- [ ] Full ABAC policy model: only a small fixed set of contextual checks currently exists.
- [ ] Document access: current protected endpoint returns metadata and decision evidence, not file bytes.
- [ ] DOWNLOAD: action can be evaluated, but secure binary delivery is not implemented.
- [ ] Audit integrity: database hash chaining exists; independent signed checkpoints and verification tooling do not.
- [ ] Session deployment: secure cookie and CORS settings must be configured for the actual HTTPS hosting origins.
- [ ] Policy versioning: schema exists, but live decision-to-version binding and immutable publication are not implemented.

## Prioritized prototype roadmap

### P0 — safe public prototype foundation
- [ ] Remove fixed demo credentials and scan code/history for secrets.
- [ ] Keep demo seeding off by default and require local environment-provided passwords.
- [ ] Parameterize CORS origins and enforce secure cookies in HTTPS deployment.
- [ ] Add public deployment configuration and operational documentation.
- [ ] Complete automated authorization regression tests.

### P1 — protected document delivery
- [ ] Add private object/file storage abstraction.
- [ ] Add authenticated upload and streaming download gateway.
- [ ] Re-evaluate authorization on every download request.
- [ ] Validate file size/type and store content hashes.
- [ ] Prevent public storage URLs and path traversal.

### P2 — policy sandbox
- [ ] Create a typed policy model for roles, attributes, resources, actions, and conditions.
- [ ] Simulate proposed policy against a read-only synthetic or approved snapshot.
- [ ] Show ALLOW/DENY changes, affected identities/resources, and explanation evidence.
- [ ] Add impact graph and before/after comparison.
- [ ] Ensure simulation cannot mutate live policy or grants.

### P3 — immutable policy lifecycle
- [ ] Draft, review, approve, and publish policy versions.
- [ ] Make published versions immutable at the application and database layers.
- [ ] Record author, approver, publication time, and policy hash.
- [ ] Associate every decision with the exact policy version used.
- [ ] Roll back by publishing a new version, never rewriting history.

### P4 — Policy Impact Verification (PIV)
- [ ] Persist simulation snapshot and predicted decision set.
- [ ] Observe decisions made after publication.
- [ ] Compare predicted versus observed outcomes.
- [ ] Distinguish matched, unexpected, and not-yet-observed cases.
- [ ] Avoid claiming verification for scenarios with no observed request.

### P5 — historical replay and audit assurance
- [ ] Reconstruct historical decisions from immutable policy and evidence snapshots.
- [ ] Compare historical and current policy outcomes.
- [ ] Add audit-chain verifier and tamper-detection tests.
- [ ] Add independently stored signed checkpoints with protected signing-key custody.

### P6 — security intelligence and polish
- [ ] Advisory anomaly detection for unusual access patterns.
- [ ] Admin analytics and exportable audit reports.
- [ ] Security regression suite for BOLA/IDOR, privilege escalation, CSRF, session handling, injection, and revocation.
- [ ] Accessibility, responsive UI, and polished synthetic-data demo.

## Explicit non-goals / limitations

- Revocation cannot retract data already downloaded or copied.
- A hash chain alone is not absolute immutability.
- Anomaly detection must not silently grant access or replace authorization.
- No real campus confidential records should be used in the hackathon demo.
