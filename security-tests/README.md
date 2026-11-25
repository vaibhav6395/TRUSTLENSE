# TRUSTLENS security demonstration and test plan

Use a disposable local database, synthetic users and harmless test files only.

## Automated checks

Run backend and frontend checks from the repository root:

~~~bash
(cd backend && mvn test)
(cd frontend && npm install && npm test && npm run build)
~~~

The backend suite covers the deterministic policy core and private file-storage validation. CI also builds both container images, runs the frontend dependency audit and executes the revocation smoke test. The smoke test enrolls TOTP for both demo accounts before exercising grant revocation.

## Identity and MFA checks

- Faculty signup creates a PENDING request only; password login must fail until an administrator approves it.
- A non-administrator must receive HTTP 403 from the faculty review and account-management endpoints.
- Password verification returns an MFA challenge, not an authenticated session. Protected document APIs must remain unavailable before MFA verification.
- First-time MFA enrollment stores an AES-GCM encrypted secret, returns ten recovery codes once, and creates a session only after a valid TOTP code.
- A recovery code can be used once; replaying it must fail. Each MFA challenge permits at most five verification attempts.
- Five password failures for the same username/address are throttled for 15 minutes.
- Disabling an account blocks its existing session on its next protected request; the last active SECURITY_ADMIN and the current administrator cannot be disabled.

## Required end-to-end adversarial demonstration

Use two browser sessions or separate cookie jars:
- admin: SECURITY_ADMIN, configured with DEMO_ADMIN_PASSWORD.
- demo: FACULTY, configured with DEMO_FACULTY_PASSWORD.

1. Sign in as admin and upload a harmless synthetic PDF as CONFIDENTIAL.
2. Create a DOWNLOAD grant for demo with a short future expiry.
3. Sign in as demo and request the document download. Confirm it succeeds.
4. Save the application URL /api/v1/documents/{id}/content (this is not a bearer token; it requires the authenticated session).
5. While demo remains signed in, revoke the grant as admin.
6. Retry the exact same content URL from demo's still-active session. It must return HTTP 403.
7. Retry POST /api/v1/documents/{id}/access-check with action DOWNLOAD from demo's still-active session and valid CSRF token. It must return a denied decision; the content route must remain forbidden.
8. Inspect Decisions and Audit history. Confirm the denial, reason code, grant creation and revocation are visible.

Expected policy outcomes:
- Active matching grant: ALLOW.
- Revoked or expired grant: DENY on the next protected request.
- Public classification: READ may be allowed; DOWNLOAD is not implied.
- Disabled account or retired document: DENY, including for owners.
- Missing permission: DENY by default.

## Upload validation checks

- Upload a PDF whose bytes begin with a PDF signature: accepted.
- Rename arbitrary binary bytes to .pdf: rejected when its declared type/signature does not match.
- Upload unsupported archive or executable types: rejected.
- Upload larger than 10 MB: rejected.
- Attempt a path traversal storage key: rejected by storage-key validation.
- Confirm stored files are not inside the frontend static web root and that the database contains only a generated storage key, not a public URL.

## Important boundaries

- The application re-evaluates access for every new download request. It cannot recall bytes already downloaded or copied.
- A database hash chain provides tamper evidence only under its database and key trust assumptions; it is not absolute immutability.
- These checks are a hackathon demonstration plan, not a substitute for a professional penetration test.
