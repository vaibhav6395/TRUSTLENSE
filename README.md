# TRUSTLENS

**Explainable, continuously enforced access governance for campus document exchange.**

TRUSTLENS is a hackathon prototype for secure campus document exchange. It combines server-side sessions, deny-by-default authorization, role and contextual checks, expiring/revocable grants, protected file delivery, explainable decisions, and hash-linked audit events.

> **Status:** Hackathon prototype. The core document-sharing flow now includes private uploads and a server-mediated download gateway. It is not yet production-ready or independently security-certified. Full policy authoring/publication, PIV, historical replay, independent audit checkpoints, malware scanning, production identity provisioning, and operational hardening remain incomplete.

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

- backend/ — Java 21, Spring Boot, Spring Security, PostgreSQL
- frontend/ — React, TypeScript, Vite
- database/ — database notes
- api-contract/ — OpenAPI contract
- docs/ — architecture, threat model, setup, deployment, roadmap
- security-tests/ — security test plan

## Local development

Prerequisites: Git, Java 21, Maven, Node.js 20+, npm, Docker Engine and Docker Compose.

1. Clone the repository and create local environment configuration:

~~~bash
git clone https://github.com/vaibhav6395/TRUSTLENSE.git
cd TRUSTLENSE
cp .env.example .env
~~~

Edit .env and replace every sample password with unique local values. Never commit .env.

2. Start PostgreSQL:

~~~bash
docker compose up -d db
docker compose ps
~~~

On systems where the Compose v2 plugin is not installed but the standalone command is available, use docker-compose up -d db and docker-compose ps.

3. Start the backend with synthetic demo data. The two demo passwords must be set in your local environment and must each be at least 14 characters:

Kali/Linux/macOS:

~~~bash
cd backend
set -a
source ../.env
set +a
DEMO_DATA_ENABLED=true COOKIE_SECURE=false mvn spring-boot:run
~~~

PowerShell:

~~~powershell
cd backend
$env:DB_URL = "jdbc:postgresql://localhost:5432/trustlens"
$env:DB_USERNAME = "trustlens"
$env:DB_PASSWORD = "the-same-value-as-POSTGRES_PASSWORD-in-your-local-env-file"
$env:DEMO_DATA_ENABLED = "true"
$env:DEMO_FACULTY_PASSWORD = "your-unique-password-at-least-14-characters"
$env:DEMO_ADMIN_PASSWORD = "a-different-unique-password-at-least-14-characters"
$env:COOKIE_SECURE = "false"
mvn spring-boot:run
~~~

4. In another terminal:

~~~bash
cd frontend
npm install
npm run dev
~~~

Open http://localhost:5173. The demo usernames are demo and admin; their passwords are the values you configured in DEMO_FACULTY_PASSWORD and DEMO_ADMIN_PASSWORD. There is no built-in shared demo password.

Demo seeding is disabled by default. Use only a disposable local database and synthetic data. Never enable demo seeding in a shared or deployed environment.

## API highlights

- POST /api/v1/documents — authenticated multipart upload (title, classification, file).
- GET /api/v1/documents/{id} — metadata read with a fresh READ decision.
- GET /api/v1/documents/{id}/content — protected attachment download with a fresh DOWNLOAD decision.
- POST /api/v1/grants — SECURITY_ADMIN creates a READ or DOWNLOAD grant, optionally with ISO-8601 expiry.
- POST /api/v1/grants/{id}/revoke — SECURITY_ADMIN revokes a grant.
- GET /api/v1/decisions and GET /api/v1/audit-events — inspectable security history.

All state-changing API calls require the session's CSRF token. Do not create public object-storage URLs or bypass the application download gateway.

## Deployment

See docs/DEPLOYMENT.md. The included production Compose file is a deployment starting point, not a hosted application: you must provision a Linux host, domain, HTTPS reverse proxy, secrets and backups. Keep PostgreSQL private, set COOKIE_SECURE=true, set the exact PUBLIC_APP_ORIGIN, and leave demo seeding disabled.

## Verification

~~~bash
cd backend && mvn test
cd ../frontend && npm install && npm test && npm run build && npm audit --audit-level=high
~~~

A successful build does not establish production security. Review docs/threat-model.md and security-tests/README.md.

## License

No license has been selected yet. Until a license is added, do not assume that the code is available for reuse under an open-source license.
