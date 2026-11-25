# Architecture

TRUSTLENS is a modular monolith with independently developed frontend and backend applications.

```text
React UI
  |
  | HTTPS / JSON
  v
Spring Boot controllers
  |
  v
Application services
  |--------------------|
  v                    v
Authorization service  Audit service
  |                    |
  v                    v
Policy evaluator       Audit events
  |                    |
  +---------+----------+
            v
       PostgreSQL
```

## Authorization request path

1. Authenticate the request using a server-side session.
2. Resolve the actor from the authenticated security context.
3. Load the target resource and current policy/grant state.
4. Evaluate role, department, classification, action, account status, grant expiry, and revocation.
5. Default to deny when required evidence is absent or evaluation fails.
6. Persist a decision explanation and policy version for traceability.
7. Deliver protected content only after the server-side decision allows it.

The client must not supply trusted actor identity, role, department, classification, or policy version.

## Revocation semantics

Once a grant is revoked, subsequent server-mediated requests must be denied. Revocation cannot retract content already downloaded, copied, or remembered.

## Policy simulation and publication

Simulation is read-only and reports predicted outcomes against a defined snapshot. Publication creates a new immutable version; historical decision records retain the policy version and evidence used at decision time.

## Audit integrity

A hash chain can reveal modification or deletion within a sequence, but does not make the database impossible to alter. Production-grade assurance requires protected signing keys and independently stored signed checkpoints.


## Implemented access governance modules

- **Identity and session:** BCrypt credentials, administrator-approved faculty onboarding, mandatory TOTP MFA, one-time recovery codes, login throttling and per-request account/role refresh.
- **RBAC:** FACULTY, STUDENT, STAFF and SECURITY_ADMIN role assignments. Administrative role changes are server-side and audited.
- **ABAC:** department, clearance, classification, document tags, structured user/resource attributes, ownership, resource state and grant state are loaded from PostgreSQL for every read/download decision.
- **Sharing:** owners can grant within their department; SECURITY_ADMIN can grant across departments. READ and DOWNLOAD are distinct permissions. Grants support scheduled start, expiry, purpose, download caps and revocation.
- **Document lifecycle:** private uploads, metadata editing, classification, tags, structured attributes, required clearance, retention expiry and retirement.
- **Policy governance:** constrained JSON rules, backend simulation, draft/submission, independent administrator review and immutable published versions.
- **Explainability:** decisions retain policy version and attribute evidence. Historical replay re-evaluates the original evidence under its original version; PIV compares recent observations against a published version.

## Policy decision semantics

The policy engine applies system guardrails before configurable policy rules:
1. Disabled accounts are denied.
2. Retired/inactive or retention-expired documents are denied.
3. User clearance below the document's required clearance is denied.
4. Remaining rules are evaluated by descending priority; the first matching ALLOW or DENY rule determines the outcome.
5. If no rule matches, access is denied.

Policy JSON is declarative data. No JavaScript, SQL or user-supplied executable expression is evaluated. The active policy is selected from the highest published version. Drafts and pending approvals never affect live access decisions.

## Sharing and revocation semantics

A grant is valid only when its start time has arrived, its expiry has not passed, it has not been revoked, and any download quota remains. Each request checks the current grant row. A revoked grant, retired document, disabled account or expired retention period blocks the next server-mediated request. The system cannot revoke bytes that a user has already downloaded.

## Current scope boundary

The current implementation is a hackathon prototype. It still needs campus SSO/directory verification, malware scanning/quarantine, external signed audit checkpoints, operational monitoring, backup/restore validation and independent security review before real institutional data is used.
