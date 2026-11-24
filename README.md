# TRUSTLENS

**Explainable, continuously enforced access governance for campus document exchange.**

TRUSTLENS is a hackathon prototype for secure campus document exchange. It combines server-side sessions, deny-by-default authorization, role and contextual checks, expiring/revocable grants, protected file delivery, explainable decisions, and hash-linked audit events.

> **Status:** Hackathon prototype. The core document-sharing flow includes private uploads and a server-mediated download gateway. It is not production-ready or independently security-certified. Full policy authoring/publication, PIV, historical replay, independent audit checkpoints, malware scanning, production identity provisioning, and operational hardening remain incomplete.

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
- Deny-by-default authorization using authenticated identity, account state, document state, owner, public READ, active READ/DOWNLOAD grants, and same-department SECURITY_ADMIN READ.
- Grant creation with expiry validation, admin-only revocation, and audit events.
- Private server-side file storage with generated storage keys, 10 MB limit, allowlisted PDF/TXT/PNG/JPEG types, basic content-signature checks, and SHA-256 metadata.
- Authenticated upload and download endpoints. Downloads are streamed only after a fresh authorization decision; storage paths are never exposed as public URLs.
- Persistent ALLOW/DENY decisions with reason codes and evidence snapshots.
- Hash-linked audit events for access decisions, grant creation/revocation and document uploads.
- React interface for sign-in, document upload/read/download, grant management, decisions, audit history and a read-only synthetic policy sandbox.
- Docker Compose development and production topology, plus CI build, test, dependency-audit and container-build jobs.

## Known gaps

- ABAC rules are currently a small deterministic policy, not a configurable policy language.
- The Policy Sandbox remains a browser-only synthetic simulation; it does not publish policy or affect live authorization.
- Policy versioning, approvals, PIV, historical replay and independent signed audit checkpoints are not implemented.
- A downloaded file cannot be recalled after the recipient has saved or copied it.
- File type checks are not malware scanning; use only synthetic demo files.
- Production user provisioning, rate limiting, monitoring, backup/restore drills and independent security review remain required.

## Repository layout

- `backend/` — Java 21, Spring Boot, Spring Security, PostgreSQL
- `frontend/` — React, TypeScript, Vite
- `database/` — database notes
- `api-contract/` — OpenAPI contract
- `docs/` — architecture, threat model, setup, deployment, roadmap
- `security-tests/` — security test plan

## Fetching and switching branches

The repository uses separate branches for the stable baseline and ongoing work. Fetch the branch you need explicitly so you do not accidentally work on the wrong version.

### Clone a specific branch

Clone the stable `main` branch:

```bash
git clone --single-branch --branch main https://github.com/vaibhav6395/TRUSTLENSE.git
cd TRUSTLENSE
```

Or clone the production hardening work branch:

```bash
git clone --single-branch --branch production/phase-1-safety-baseline https://github.com/vaibhav6395/TRUSTLENSE.git
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

Switch to the production hardening branch:

```bash
git switch --track origin/production/phase-1-safety-baseline
```

If you already created a local branch with that name, use:

```bash
git switch production/phase-1-safety-baseline
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

## Local hackathon stack (recommended)

For the quickest complete local setup, use the one-command Docker Compose stack in [docs/LOCAL-HACKATHON-RUNBOOK.md](docs/LOCAL-HACKATHON-RUNBOOK.md). It starts PostgreSQL, the backend and frontend together at **http://localhost:5173** using synthetic demo accounts. This mode is for localhost-only hackathon demonstrations, not internet exposure.

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

Edit `.env`. Set `POSTGRES_PASSWORD` and set `DB_PASSWORD` to the **same value**. Replace both demo password placeholders with different unique passwords of at least 14 characters. Keep `.env` private; never commit it.

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

Keep this terminal open. On first startup, Flyway applies the database migrations and the demo initializer creates the `demo` and `admin` users using the passwords in `.env`.

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

## API highlights

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

A successful build does not establish production security. Review [docs/threat-model.md](docs/threat-model.md) and [security-tests/README.md](security-tests/README.md).

## License

No license has been selected yet. Until a license is added, do not assume that the code is available for reuse under an open-source license.
