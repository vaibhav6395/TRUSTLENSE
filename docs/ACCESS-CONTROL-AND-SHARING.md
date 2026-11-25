# TRUSTLENSE access control, sharing and policy lifecycle

## Request architecture

Every protected document read and download is evaluated by the backend. The frontend does not supply trusted roles, departments, clearance, ownership or policy versions.

1. Spring Security authenticates the session and refreshes account enabled state and role assignments from PostgreSQL.
2. The document service loads the current user, document metadata, role set, user attributes, document attributes, grant state and active policy version.
3. The ABAC engine applies non-overridable guardrails: disabled accounts, inactive/retired documents and insufficient clearance are denied.
4. Policy rules are ordered by descending priority. The first matching rule returns ALLOW or DENY. No matching rule means DENY.
5. The service writes a decision, reason code, explanation and evidence snapshot linked to the active policy version.
6. Only an ALLOW result can proceed to document content delivery. Download delivery is a private server-side file stream, not a public storage URL.

## Built-in ABAC rule structure

Rules are JSON data, not executable scripts:

```json
{
  "rules": [
    {
      "id": "faculty-confidential-research",
      "effect": "ALLOW",
      "priority": 100,
      "actions": ["READ"],
      "subject": {
        "roles": ["FACULTY"],
        "sameDepartment": true,
        "clearanceMin": 2,
        "attributes": { "employmentStatus": "ACTIVE" }
      },
      "resource": {
        "classifications": ["CONFIDENTIAL"],
        "tagsAny": ["research"],
        "attributes": { "dataOwner": "CSE" }
      },
      "conditions": { "grantRequired": true },
      "reasonCode": "FACULTY_RESEARCH_GRANT",
      "description": "Active CSE faculty with sufficient clearance and a current grant may read research files."
    }
  ]
}
```

Supported attributes:
- Subject: roles, departments, sameDepartment, clearanceMin and arbitrary scalar attributes.
- Resource: classifications, departments, sameDepartment, tagsAny and arbitrary scalar attributes.
- Conditions: owner, grantRequired and grantPermission.
- Actions: READ, DOWNLOAD or *.
- Effects: ALLOW or DENY.
- Priority: larger values are evaluated first.

The system guardrails execute before configurable rules and cannot be bypassed by a policy author. The engine never evaluates client-side rules as authorization. For a matching rule, the highest-priority matching rule wins; avoid ambiguous overlapping rules with equal priority.

## Faculty sharing workflow

- Document owners can share their own active documents with enabled recipients in the same department.
- SECURITY_ADMIN can share across departments.
- A grant specifies READ or DOWNLOAD, optional start time, expiry, purpose and optional download cap.
- A scheduled grant is inactive until its start time. An expired, revoked or exhausted grant does not authorize new requests.
- Owners, grant creators and SECURITY_ADMIN can revoke a grant.
- Revocation is checked on every request; previously downloaded copies cannot be recalled.
- Inbound, outbound and administrator-managed grants appear in the grant ledger.

## Document lifecycle

- Uploads use generated storage keys, private storage, allowlisted formats and a 10 MB size limit.
- Metadata includes description, classification, tags, required clearance, structured resource attributes and retention expiry.
- Owners and SECURITY_ADMIN can edit metadata. Only SECURITY_ADMIN can publish PUBLIC or RESTRICTED classifications.
- Owners and SECURITY_ADMIN can retire a document. Retirement revokes all active grants.
- Retention expiry denies future reads and downloads.

## RBAC administration

SECURITY_ADMIN can assign FACULTY, STUDENT, STAFF and SECURITY_ADMIN roles; change department, clearance and scalar user attributes; and enable/disable accounts. The last active SECURITY_ADMIN cannot be disabled or demoted. Session refresh enforces account and role changes on the next protected request.

## Policy lifecycle

1. Administrator edits a JSON policy and runs server-side simulation against synthetic scenarios.
2. Administrator creates a DRAFT and submits it for approval.
3. A different SECURITY_ADMIN must approve or reject the pending change.
4. Approval publishes an immutable version. The highest published version is the active policy.
5. New access decisions record the exact policy version used.

## Historical replay and Policy Impact Verification

- A user can replay their own decision; SECURITY_ADMIN can replay all decisions.
- Replay evaluates the recorded evidence snapshot against the original policy version and reports whether the recorded ALLOW/DENY outcome is reproducible.
- Policy Impact Verification evaluates up to 100 recent decision evidence snapshots against a selected published version and reports gained access, lost access and unchanged outcomes.
- PIV is a historical sample, not a guarantee of future behavior.

## APIs added

- `GET /api/v1/policies`, `GET /api/v1/policies/{id}`
- `POST /api/v1/policies/simulate`, `POST /api/v1/policies/drafts`
- `POST /api/v1/policies/{id}/submit`, `/approve`, `/reject`, `/impact-verify`
- `PATCH /api/v1/documents/{id}`, `POST /api/v1/documents/{id}/retire`
- `POST /api/v1/grants`, `POST /api/v1/grants/{id}/revoke`
- `POST /api/v1/admin/users/{username}/roles/{roleCode}`, `DELETE` same path
- `PATCH /api/v1/admin/users/{username}/access-profile`
- `POST /api/v1/decisions/{id}/replay`



## Dynamic roles and authorities

SECURITY_ADMIN can create custom role codes, describe them, assign supported platform authorities, update custom roles and delete them only after all user assignments have been removed. Built-in FACULTY, STUDENT, STAFF and SECURITY_ADMIN roles are marked system-managed and cannot be edited or deleted. User role assignment accepts existing built-in or custom roles; it no longer silently creates unknown roles.

Supported platform authorities:

| Authority | Protected capability |
|---|---|
| `PERM_USER_MANAGE` | Review faculty requests and manage user accounts |
| `PERM_ROLE_MANAGE` | Create/update/delete custom roles and assign authorities |
| `PERM_POLICY_MANAGE` | Simulate, review and publish policies |
| `PERM_AUDIT_READ` | Read platform-wide audit history |

The SECURITY_ADMIN built-in role receives all four authorities during demo or production bootstrap. The role-authority catalogue is allowlisted by the backend; arbitrary permission strings cannot be added. Custom role memberships are also available as role attributes to ABAC policy rules. Platform authorities are distinct from document-level READ/DOWNLOAD grants.

Role management API: `GET /api/v1/admin/roles`, `GET /api/v1/admin/roles/authorities`, `POST /api/v1/admin/roles`, `PUT /api/v1/admin/roles/{code}`, and `DELETE /api/v1/admin/roles/{code}`. Account role assignment remains under `/api/v1/admin/users/{username}/roles/{roleCode}`.

## Operational walkthrough

1. Start the local stack and sign in using a synthetic demo account; complete MFA enrollment on first sign-in.
2. Submit a faculty request. Sign in as SECURITY_ADMIN in a separate browser session and review the pending request.
3. Approve or reject the request. Sign in as the approved faculty user and enroll TOTP before continuing.
4. Upload a synthetic document. Set classification, required clearance, tags and any retention date.
5. Create a grant for the recipient, selecting READ or DOWNLOAD and any start/expiry/quota constraints.
6. Sign in as the recipient and test metadata read and file download separately; READ does not imply DOWNLOAD.
7. Revoke the grant as an authorized owner, grant creator or administrator. Retry both the document read API and content endpoint using the same still-authenticated session. The next request must be denied and recorded.
8. As a SECURITY_ADMIN, create and simulate a policy draft. Submit it, then use a different SECURITY_ADMIN account to approve or reject it.
9. Inspect decision and audit history. Replay a decision against its original evidence/policy and run PIV against a bounded historical sample.

## Security and deployment limits

TRUSTLENS is a hackathon prototype. It is not a substitute for campus SSO, a verified institutional directory, malware scanning, professional security review or operational monitoring. A file already downloaded by a recipient cannot be recalled. Audit hash linking is tamper evidence, not absolute immutability.
