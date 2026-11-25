# TRUSTLENSE

**Explainable, continuously enforced access governance for campus document exchange.**

TRUSTLENS is a hackathon prototype for secure campus document exchange. It combines server-side sessions, deny-by-default authorization, role and contextual checks, expiring/revocable grants, protected file delivery, explainable decisions, and hash-linked audit events.

> **Status:** Expanded hackathon prototype with administrator-approved faculty onboarding, mandatory TOTP MFA, live RBAC/ABAC evaluation, controlled sharing, document lifecycle, policy versioning/approval, historical replay and Policy Impact Verification. It is not production-certified; campus SSO, malware scanning, independent audit checkpoints and operational hardening remain deployment requirements.

## Challenge coverage

| NexHack Problem Statement 07 requirement | Current implementation |
|---|---|
| Model controlled sharing with fictional users/documents | Environment-gated synthetic demo seeding; PostgreSQL records |
| Combine roles and attributes | Server-derived roles, department matching, classification, document state, grant permission and expiry |
| Enforce every request | Authenticated API; read and download gateway evaluate current server-side state |
| Revoke immediately | Revoked/expired grants are excluded from each subsequent authorization query; no reusable bearer download URL |
| Handle active sessions and policy state | Session authentication is separate from current database grant checks |
| Explain and audit | Persisted ALLOW/DENY decisions, reason codes, evidence snapshots and hash-linked audit events |
| Demonstrate adversarial checks | See security test plan for grant revocation, direct URL retry and API retry |

## Implemented

- Database-backed login/logout with BCrypt, server-side sessions, CSRF bootstrap and session ID rotation.
- Faculty signup requests are stored separately from active users; pending/rejected requests cannot authenticate.
- SECURITY_ADMIN-only approval/rejection creates the faculty account and records the reviewer and decision in the hash-linked audit history.
- Admin account inventory and enable/disable controls; self-disable and disabling the last active SECURITY_ADMIN are blocked, and active sessions are invalidated on their next request after disablement.
- Password verification is only the first authentication stage. A short-lived, single-use MFA challenge must be completed before an authenticated session is issued.
- TOTP enrollment and verification, AES-256-GCM encrypted MFA secrets, ten single-use SHA-256-hashed recovery codes, a five-attempt limit per MFA challenge, and a database-backed 5-failures/15-minute password login throttle.
- Production first-admin bootstrap is performed by the backend from environment variables; no public administrator signup endpoint exists.
- Deny-by-default RBAC/ABAC authorization using live roles, department, clearance, classification, tags, structured subject/resource attributes, document lifecycle, retention, ownership and active grants.
- Owner/admin controlled sharing with READ or DOWNLOAD grants, same-department faculty constraints, scheduled start, expiry, purpose, download limits, owner/creator/admin revocation and audit events.
- Private server-side file storage with generated storage keys, 10 MB limit, allowlisted PDF/TXT/PNG/JPEG types, basic content-signature checks, and SHA-256 metadata.
- Authenticated upload and download endpoints. Downloads are streamed only after a fresh authorization decision; storage paths are never exposed as public URLs.
- Persistent ALLOW/DENY decisions with reason codes, explanations, complete evidence snapshots and immutable policy-version references.
- JSON policy studio with server-side simulation, draft/submit/review/publish lifecycle and independent two-administrator approval.
- Historical decision replay against the original policy/evidence and Policy Impact Verification against up to 100 recent observed decisions.
- Document metadata editing, custom ABAC attributes, required clearance, retention expiry and retirement with automatic grant revocation.
- Administrator role and authority management, including custom role creation, supported platform permission assignment, role lifecycle controls and user role assignment; user access-profile management includes clearance and custom attributes.
- Hash-linked audit events for access decisions, grant creation/revocation and document uploads.
- React interface for sign-in, document upload/read/download, metadata editing, owner-controlled sharing, role/access-profile administration, live policy simulation and publication, historical replay, decisions and audit history.
- Docker Compose development and production topology, plus CI build, test, dependency-audit and container-build jobs.

## Known gaps

- Policy rules use a constrained JSON rule format rather than a general-purpose policy language. Rules are data, not executable scripts.
- PIV compares a bounded sample of recorded decisions; it does not guarantee future outcomes.
- Independent signed audit checkpoints, malware scanning, campus SSO/directory integration, administrator-assisted MFA reset, monitoring, backup/restore drills and independent security review remain deployment hardening tasks.
- A downloaded file cannot be recalled after the recipient has saved or copied it.
- File type checks are not malware scanning; use only synthetic demo files.
- Administrator-assisted MFA reset and campus directory/SSO verification are not implemented yet.
- Institutional email/faculty-ID verification is a manual administrator process; no campus directory or SSO connector is configured.
- Monitoring, backup/restore drills and independent security review remain required.

## End-to-end application flow

The recommended demonstration follows this order:

1. **Bootstrap identity:** start the backend with the local demo profile, or create the first production administrator through the backend-only bootstrap configuration.
2. **Faculty onboarding:** submit a faculty signup request. It remains PENDING and cannot log in until a SECURITY_ADMIN reviews and approves it.
3. **MFA sign-in:** sign in with username/password, complete TOTP enrollment or verification, and receive the authenticated session only after MFA succeeds.
4. **Document publishing:** upload a synthetic document, set its classification, tags, required clearance and optional retention expiry. The document remains private to server-side storage.
5. **Controlled sharing:** the owner grants READ or DOWNLOAD access to an eligible recipient; the grant may have a start time, expiry and download cap.
6. **Request-time enforcement:** every metadata read and content download is authorized against current account, role, attributes, document state, active policy and grant state.
7. **Revoke and verify:** revoke a grant, then retry the document API and content endpoint with the same authenticated session. The subsequent request must be denied.
8. **Govern policy:** simulate a JSON policy, save and submit a draft, obtain review from a different SECURITY_ADMIN, and publish the immutable version.
9. **Investigate:** inspect decisions and audit events, replay a recorded decision against its original policy/evidence, and run bounded Policy Impact Verification.

For the detailed walkthrough, roles and API sequence, see [docs/USER-WORKFLOWS.md](docs/USER-WORKFLOWS.md). For enforcement details, see [docs/ACCESS-CONTROL-AND-SHARING.md](docs/ACCESS-CONTROL-AND-SHARING.md).

## Repository layout

- `backend/` — Java 21, Spring Boot, Spring Security, PostgreSQL
- `frontend/` — React, TypeScript, Vite
- `database/` — database notes
- `api-contract/` — OpenAPI contract
- `docs/` — architecture, threat model, setup, deployment, identity/MFA, access-control/sharing workflows and roadmap
- `security-tests/` — security test plan

## Fetching and switching branches

The current hackathon implementation is developed on feature/complete-zero-trust-policy-workflow and reviewed through [PR #6](https://github.com/vaibhav6395/TRUSTLENSE/pull/6) into main. The PR is not merged; main remains unchanged. Fetch the feature branch explicitly to run the complete current implementation.

### Clone a specific branch

Clone the stable `main` branch:

```bash
git clone --single-branch --branch main https://github.com/vaibhav6395/TRUSTLENSE.git
cd TRUSTLENSE
```

Clone the current hackathon implementation branch:

```bash
git clone --single-branch --branch feature/complete-zero-trust-policy-workflow https://github.com/vaibhav6395/TRUSTLENSE.git
cd TRUSTLENSE
```

### If TRUSTLENSE is already cloned

Fetch the latest remote branch information:

```bash
cd ~/Projects/TRUSTLENSE
git fetch origin --prune
git branch -r
```

Switch to the stable branch:

```bash
git switch main
git pull --ff-only origin main
```

Switch to the current hackathon implementation branch:

```bash
git switch --track origin/feature/complete-zero-trust-policy-workflow
```

If the local branch already exists:

```bash
git switch feature/complete-zero-trust-policy-workflow
git pull --ff-only
```

To update the currently checked-out branch without switching branches:

```bash

git pull --ff-only
```

### See branches and confirm your current branch

```bash
git branch -a
git branch --show-current
git status
```

Before switching branches, commit or safely stash your own changes. Do not use `git reset --hard` or delete untracked files just to make a branch switch succeed; inspect `git status` first.

## Local development (Kali Linux / Linux / macOS)

This is the shortest supported local setup. Use synthetic data and a disposable local database only.

### 1. Install prerequisites

- Git
- Java 21
- Maven
- Node.js 20+ and npm
- Docker Engine with either the Compose v2 plugin (`docker compose`) or standalone Compose (`docker-compose`)

Check the tools:

```bash
java -version
mvn -version
node --version
npm --version
docker --version
docker compose version || docker-compose version
```

### 2. Clone and configure the project

```bash
git clone https://github.com/vaibhav6395/TRUSTLENSE.git
cd TRUSTLENSE
cp .env.example .env
```

Edit `.env`. Set `POSTGRES_PASSWORD` and set `DB_PASSWORD` to the **same value**. Replace both demo password placeholders with different unique passwords of at least 14 characters. Generate `MFA_ENCRYPTION_KEY` using `openssl rand -base64 32` and keep it stable: losing or changing it makes existing authenticator secrets unreadable. Keep `.env` private; never commit it.

For local HTTP development, `COOKIE_SECURE=false` is required. The example file already sets this and allows the Vite origin `http://localhost:5173`.

### 3. Start PostgreSQL

Use the Compose command available on your system:

```bash
docker compose up -d db
docker compose ps
```

If your installation uses the standalone Compose executable instead:

```bash
docker-compose up -d db
docker-compose ps
```

Wait until the database container reports healthy before starting the backend.

**Without Docker:** install and start PostgreSQL locally, then create a database named `trustlens` and a login role named `trustlens`. Set `DB_PASSWORD` in `.env` to that role's password. The database URL defaults to `jdbc:postgresql://localhost:5432/trustlens`.

### 4. Start the backend

Open terminal 1 from the repository root:

```bash
cd TRUSTLENSE
set -a
source .env
set +a
cd backend
DEMO_DATA_ENABLED=true COOKIE_SECURE=false mvn spring-boot:run
```

Keep this terminal open. On first startup, Flyway applies the database migrations and the demo initializer creates the `demo` and `admin` users using the passwords in `.env`. On first sign-in, each account must enroll an authenticator app before a session is issued. The setup key is displayed once; enter it into an authenticator app and submit its current six-digit code. Ten single-use recovery codes are then displayed once—save them privately.

If your Maven installation does not resolve the Spring Boot plugin using the short goal, run:

```bash
mvn org.springframework.boot:spring-boot-maven-plugin:3.4.4:run
```

### 5. Start the frontend

Open terminal 2:

```bash
cd TRUSTLENSE/frontend
npm install
npm run dev
```

Keep this terminal open. Vite serves the app at **http://localhost:5173** and proxies `/api` requests to the backend at `http://localhost:8080`.

### 6. Verify the services and sign in

Open terminal 3:

```bash
curl -i http://localhost:8080/api/v1/health
curl -i http://localhost:5173/api/v1/health
```

Both should return HTTP `200` with service status `UP`. Then open **http://localhost:5173**.

| Demo account | Username | Password |
|---|---|---|
| Faculty | `demo` | Value configured in `DEMO_FACULTY_PASSWORD` |
| Security administrator | `admin` | Value configured in `DEMO_ADMIN_PASSWORD` |

There are no built-in or shared demo passwords. Do not paste passwords into terminal commands as standalone input; enter them only in the application or the relevant protected configuration.

> **Existing database note:** Demo seeding does not reset the password of a username that already exists. If you change a demo password in `.env` after the account has already been created, the existing database password remains unchanged. For a disposable local demo database, you can remove the Compose volume and recreate it, but this permanently deletes the local database:
>
> ```bash
> docker compose down -v
> docker compose up -d db
> ```
>
> Use `docker-compose` instead of `docker compose` if that is the command installed on your system. Never run the volume-removal command on data you need.

### Troubleshooting

- **`curl: (7) Failed to connect to localhost port 5173`:** the frontend is not running. Start it from `frontend/` with `npm run dev` and keep that terminal open.
- **Port 8080 connection refused:** start the backend and check its terminal for startup errors.
- **Frontend API request fails:** confirm `frontend/vite.config.ts` contains the `/api` proxy to `http://localhost:8080`, then restart Vite.
- **HTTP 401 on login:** verify the username and configured password. Changing the environment variable does not reset an already-created user's password.
- **HTTP 403:** check that the browser is using the Vite origin, that `COOKIE_SECURE=false` is set for local HTTP, and that the CSRF bootstrap request succeeds. Do not disable CSRF protection.
- **Docker Compose command not found:** try `docker-compose` if the standalone executable is installed, or install the Compose plugin for Docker Engine.

## Windows (PowerShell)

The commands below assume PostgreSQL is already running locally and the database/user have been created. Configure the variables in your PowerShell session before starting the backend:

```powershell
cd backend
$env:DB_URL = "jdbc:postgresql://localhost:5432/trustlens"
$env:DB_USERNAME = "trustlens"
$env:DB_PASSWORD = "your-local-database-password"
$env:DEMO_DATA_ENABLED = "true"
$env:DEMO_FACULTY_PASSWORD = "your-unique-password-at-least-14-characters"
$env:DEMO_ADMIN_PASSWORD = "a-different-unique-password-at-least-14-characters"
$env:COOKIE_SECURE = "false"
mvn spring-boot:run
```

In a second PowerShell window:

```powershell
cd frontend
npm install
npm run dev
```

Open http://localhost:5173.

Demo seeding is disabled by default. Use only a disposable local database and synthetic data. Never enable demo seeding in a shared or deployed environment.

## Identity and MFA setup

See [docs/IDENTITY-AND-MFA.md](docs/IDENTITY-AND-MFA.md) for the faculty approval flow, first-admin bootstrap, MFA enrollment/login sequence, recovery codes and account disablement behavior. The complete operator and user walkthrough is in [docs/USER-WORKFLOWS.md](docs/USER-WORKFLOWS.md).

## Dynamic roles and authorities

Administrators with the relevant authority can create custom role codes and assign supported platform authorities. The current authority catalogue is `PERM_USER_MANAGE`, `PERM_ROLE_MANAGE`, `PERM_POLICY_MANAGE` and `PERM_AUDIT_READ`. These protect account administration, role administration, policy governance and platform-wide audit access respectively. Built-in roles are protected from editing/deletion; a custom role cannot be deleted while assigned to users. Custom role names are also available to ABAC policy rules as subject roles.

## API highlights

- `POST /api/v1/auth/signup` — submit a faculty request (does not create an active account).
- `POST /api/v1/auth/login` — verify password and issue a short-lived MFA challenge (not a session).
- `POST /api/v1/auth/mfa/setup` and `POST /api/v1/auth/mfa/verify` — enroll or verify TOTP; only verification creates the authenticated session.
- `GET /api/v1/admin/signup-requests`, `POST /api/v1/admin/signup-requests/{id}/approve`, `POST /api/v1/admin/signup-requests/{id}/reject` — SECURITY_ADMIN-only faculty review.
- `GET /api/v1/admin/users`, `POST /api/v1/admin/users/{username}/disable`, `POST /api/v1/admin/users/{username}/enable` — SECURITY_ADMIN-only account lifecycle controls.
- `POST /api/v1/documents` — authenticated multipart upload (title, classification, file).
- `GET /api/v1/documents/{id}` — metadata read with a fresh READ decision.
- `GET /api/v1/documents/{id}/content` — protected attachment download with a fresh DOWNLOAD decision.
- `POST /api/v1/grants` — SECURITY_ADMIN creates a READ or DOWNLOAD grant, optionally with ISO-8601 expiry.
- `POST /api/v1/grants/{id}/revoke` — SECURITY_ADMIN revokes a grant.
- `GET /api/v1/decisions` and `GET /api/v1/audit-events` — inspectable security history.

All state-changing API calls require the session's CSRF token. Do not create public object-storage URLs or bypass the application download gateway.

## Deployment

See [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md). The included production Compose file is a deployment starting point, not a hosted application: you must provision a Linux host, domain, HTTPS reverse proxy, secrets and backups. Keep PostgreSQL private, set `COOKIE_SECURE=true`, set the exact `PUBLIC_APP_ORIGIN`, and leave demo seeding disabled.

## Verification

```bash
cd backend && mvn test
cd ../frontend && npm install && npm test && npm run build && npm audit --audit-level=high
```

The latest CI run for commit f8d307bd6cf8642f95dcc52070e61db3e8210db7 passed all five jobs: backend tests, frontend build/tests/dependency audit, secret scan, container builds and the grant-revocation smoke test. See [CI run #112](https://github.com/vaibhav6395/TRUSTLENSE/actions/runs/45537059083). This is CI evidence for the feature branch, not production certification or proof that every end-to-end workflow is covered. A successful build does not establish production security. Review [docs/PRODUCTION-READINESS.md](docs/PRODUCTION-READINESS.md), [docs/threat-model.md](docs/threat-model.md) and [security-tests/README.md](security-tests/README.md).

## License

No license has been selected yet. Until a license is added, do not assume that the code is available for reuse under an open-source license.
