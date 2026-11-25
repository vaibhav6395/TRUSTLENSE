# TRUSTLENSE user and administrator workflows

This guide describes the end-to-end demonstration using synthetic campus data. It complements [ACCESS-CONTROL-AND-SHARING.md](ACCESS-CONTROL-AND-SHARING.md), [IDENTITY-AND-MFA.md](IDENTITY-AND-MFA.md), and [architecture.md](architecture.md).

## Actors

| Actor | Responsibility |
|---|---|
| Faculty applicant | Requests an account; cannot access protected APIs while pending |
| FACULTY | Uploads owned documents and grants eligible same-department access |
| STUDENT / STAFF | Uses only access permitted by active policy and grants |
| SECURITY_ADMIN | Reviews faculty requests, manages accounts/roles, shares across departments and governs policy versions |

The backend derives the authenticated user and current roles from the session and database. Never trust role, department, clearance, owner or grant values supplied by the browser.

## 1. First administrator and local demo

### Local hackathon run

1. Configure .env using .env.example.
2. Keep demo seeding enabled only for the local disposable environment.
3. Start PostgreSQL, backend and frontend using the README setup.
4. Sign in with the configured synthetic admin or demo account.
5. On first login, complete TOTP enrollment and store the one-time recovery codes safely.

### Production-profile bootstrap

The production profile does not use demo seeding. The first SECURITY_ADMIN is created by the backend bootstrap initializer only when explicitly enabled and configured with BOOTSTRAP_ADMIN_USERNAME and a unique BOOTSTRAP_ADMIN_PASSWORD of at least 16 characters. No public administrator signup endpoint exists. Disable/remove bootstrap credentials after the first administrator has been created.

## 2. Faculty onboarding and approval

1. Applicant submits username, institutional email, faculty identifier, department and password.
2. Backend stores a PENDING faculty request; it does not create an enabled user account.
3. Applicant cannot authenticate while the request is pending or rejected.
4. SECURITY_ADMIN reviews the request and checks institutional identity through the current manual process.
5. Admin approves or rejects the request. Approval creates the enabled FACULTY account and records the reviewer and decision.
6. Approved faculty member signs in with password and completes TOTP enrollment/verification.
7. Only after successful MFA verification does the backend establish the authenticated session.

**Important:** campus email/domain or faculty identifier is not automatically verified against an institutional directory. This remains a manual review step.

## 3. Authentication and session behavior

1. POST /api/v1/auth/login validates the password and returns a short-lived MFA challenge.
2. New users complete setup using POST /api/v1/auth/mfa/setup; enrolled users proceed directly to verification.
3. POST /api/v1/auth/mfa/verify consumes the challenge and verifies TOTP or an eligible one-time recovery code.
4. The backend rotates the session identifier and establishes the authenticated session only after successful verification.
5. CSRF protection applies to state-changing requests.
6. On authenticated requests, the session refresh filter reloads account enabled state and current roles. A disabled or deleted account is rejected on its next request; role changes take effect on the next request.

## 4. Upload and document lifecycle

1. Authenticated user uploads a synthetic PDF, TXT, PNG or JPEG file within the configured size limit.
2. Backend validates the allowed extension/content type and basic file signature, calculates SHA-256, assigns a generated storage key and stores the file privately.
3. Owner supplies document title, classification, description, tags and required clearance. Metadata can include scalar resource attributes and a retention expiry.
4. Owners and SECURITY_ADMIN can update permitted metadata. Only SECURITY_ADMIN can set PUBLIC or RESTRICTED classifications.
5. Owner or SECURITY_ADMIN can retire the document. Retirement revokes active grants.
6. Retention expiry and inactive/retired status block subsequent reads and downloads.

File signature checks are not malware scanning. Use synthetic files only until a malware scanning/quarantine control is deployed.

## 5. Grant and sharing lifecycle

1. Owner chooses a recipient and a permission: READ or DOWNLOAD.
2. Owner can share their own active document with an enabled recipient in the same department. SECURITY_ADMIN can create cross-department grants.
3. Grant may include a start time, expiry, purpose and download cap.
4. Grant is scheduled before its start time, active only during its valid window, and unusable after revocation, expiry or quota exhaustion.
5. Each read/download request loads current grant state. A previously authenticated session does not preserve a revoked grant.
6. Authorized owner, grant creator or SECURITY_ADMIN can revoke a grant.
7. A file already downloaded or copied by a recipient cannot be recalled.

**Demonstration check:** create a grant, access the file, revoke the grant, then retry both the metadata endpoint and /api/v1/documents/{id}/content using the same session. The subsequent request must be denied and leave a decision/audit record.

## 6. RBAC and ABAC evaluation

SECURITY_ADMIN can create custom role codes and assign supported administrative authorities in People & roles. The current authority catalogue controls user management, role management, policy management and platform-wide audit visibility. Built-in roles are system-managed; custom roles must be unassigned before deletion. Custom role codes can also be referenced by ABAC policy rules.

RBAC supplies role membership; ABAC adds request context such as department, clearance, classification, tags and custom scalar attributes.

For each protected request the backend:

1. Identifies the actor from the authenticated session.
2. Reloads current roles and account state.
3. Loads document classification, tags, department, custom attributes, lifecycle and retention.
4. Loads the applicable current grant and action.
5. Applies non-overridable guardrails: disabled account, inactive/retired/retention-expired document and insufficient clearance are denied.
6. Evaluates policy rules in descending priority. The first matching rule decides; no match is deny.
7. Persists ALLOW/DENY, reason code, explanation, evidence snapshot and policy version.
8. Streams file content only after an ALLOW decision.

The UI is not an authorization boundary. Hiding a control does not replace server-side checks.

## 7. Policy authoring, review and publication

1. SECURITY_ADMIN edits a constrained JSON policy.
2. Run server-side simulation against synthetic scenarios; simulation does not change live policy.
3. Save the rules as a DRAFT and submit it for review.
4. A different SECURITY_ADMIN must approve or reject the submitted version; the author cannot approve their own draft.
5. Approval publishes an immutable version. The highest published version becomes active.
6. Subsequent access decisions reference the active policy version. Drafts and pending reviews do not affect live authorization.

Avoid overlapping rules with the same priority. Use explicit priority values and reason codes so policy decisions remain understandable.

## 8. Decisions, replay, PIV and audit

- **Decision history:** inspect access attempts, outcomes, reason codes and explanations.
- **Historical replay:** re-evaluates the saved evidence snapshot with the original policy version and reports whether the original outcome is reproducible. Users can replay their own decisions; SECURITY_ADMIN can replay any decision.
- **Policy Impact Verification (PIV):** compares a proposed published version against up to 100 recent recorded evidence snapshots and reports gained access, lost access and unchanged outcomes.
- **Audit history:** inspect recorded identity, policy, document, sharing and access events without exposing protected file contents.

PIV is a bounded historical sample, not a prediction or guarantee of future access decisions. Hash-linked audit events provide tamper evidence but are not equivalent to independently signed, externally anchored audit checkpoints.

## 9. API sequence quick reference

| Workflow | API |
|---|---|
| Faculty request | POST /api/v1/auth/signup |
| Password stage | POST /api/v1/auth/login |
| MFA setup / verify | POST /api/v1/auth/mfa/setup, POST /api/v1/auth/mfa/verify |
| Review faculty requests | GET /api/v1/admin/signup-requests, POST /api/v1/admin/signup-requests/{id}/approve, POST /api/v1/admin/signup-requests/{id}/reject |
| Upload / read / download | POST /api/v1/documents, GET /api/v1/documents/{id}, GET /api/v1/documents/{id}/content |
| Create / revoke grant | POST /api/v1/grants, POST /api/v1/grants/{id}/revoke |
| Policy simulation / draft | POST /api/v1/policies/simulate, POST /api/v1/policies/drafts |
| Policy review | POST /api/v1/policies/{id}/submit, POST /api/v1/policies/{id}/approve, POST /api/v1/policies/{id}/reject |
| Replay / PIV | POST /api/v1/decisions/{id}/replay, POST /api/v1/policies/{id}/impact-verify |
| Investigation | GET /api/v1/decisions, GET /api/v1/audit-events |

Exact request schemas are defined in backend controllers and the API contract. All mutating browser requests must include the CSRF token.

## 10. Demonstration checklist

- [ ] Pending faculty request cannot sign in.
- [ ] SECURITY_ADMIN can approve/reject; a different admin identity is used for policy approval.
- [ ] MFA is required before a full authenticated session is issued.
- [ ] A user without sufficient role/attributes cannot access a document by calling the API directly.
- [ ] READ does not automatically imply DOWNLOAD.
- [ ] A grant outside its start/expiry window or after revocation/quota exhaustion is denied.
- [ ] Revoking a grant blocks the next request from the same active session.
- [ ] Retiring a document or reaching retention expiry blocks subsequent access.
- [ ] A pending policy draft does not affect live decisions; only an approved published version does.
- [ ] Decisions include reason codes and policy-version evidence; audit history is inspectable.

## Release boundary

This workflow is suitable for a synthetic-data hackathon demonstration. Do not use real confidential campus documents until institutional SSO/directory verification, malware scanning, independent audit checkpoints, monitoring, backup/restore validation and an independent security review have been completed.
