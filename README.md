# TRUSTLENS

**Explainable, continuously enforced access governance for campus document exchange.**

TRUSTLENS combines role- and attribute-based authorization with revocation, policy simulation, explainable access decisions, and tamper-evident audit history.

## Monorepo
- `backend/` — Java 21, Spring Boot 3, PostgreSQL
- `frontend/` — React, TypeScript, Vite
- `database/` — database notes and seed data
- `api-contract/` — OpenAPI contract
- `docs/` — architecture, threat model, team workflow
- `security-tests/` — security test plan

## Start locally
Prerequisites: Java 21, Node.js 20+, Docker Compose.

```bash
docker compose up -d db
cd backend && mvn spring-boot:run
# in another terminal
cd frontend && npm install && npm run dev
```

The frontend expects the API at `http://localhost:8080`.

## Security design
- Deny by default; authorization is enforced by the backend on every protected request.
- Identity and attributes come from the authenticated server-side principal, never client-supplied role or user IDs.
- Grants are checked for status, expiry, and revocation on each access.
- Files are delivered through an authenticated API; do not expose public storage URLs.
- Policy simulation is read-only; publishing creates an immutable version.
- Audit hash chaining is tamper-evident, not absolute proof. Production requires protected signing keys and independently stored checkpoints.

## Current status
This is a development foundation, not a production-ready system. Features remain incomplete until implemented and verified by automated tests. Use synthetic data only in demos.

See [docs/team-workflow.md](docs/team-workflow.md).


## Full-stack vertical slice (feature/full-stack-vertical-slice)

This branch connects a first end-to-end backend/frontend workflow:
- Database-backed session authentication using Spring Security and BCrypt.
- CSRF bootstrap endpoint and cookie-backed session handling.
- Synthetic local demo identities (opt-in).
- Server-side document visibility and access evaluation.
- READ decisions persisted to `access_decisions`.
- Hash-linked access and grant-revocation events in `audit_events`.
- Security-administrator grant listing and revocation.
- Frontend sign-in, document access request, decision explorer, audit history, and grant administration.

### Local development

Prerequisites: Java 21, Maven, Node.js 20+, npm, and Docker Compose.

1. Start PostgreSQL:

```bash
docker compose up -d db
```

2. Start the backend with local synthetic demo data enabled:

Linux / macOS / Kali:

```bash
cd backend
DEMO_DATA_ENABLED=true mvn spring-boot:run
```

PowerShell:

```powershell
cd backend
$env:DEMO_DATA_ENABLED = "true"
mvn spring-boot:run
```

3. Start the frontend in another terminal:

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5173.

### Local-only demo accounts

| Username | Password | Role |
|---|---|---|
| demo | TrustLensDemo#2026 | FACULTY |
| admin | TrustLensAdmin#2026 | SECURITY_ADMIN |

These fixed demo credentials are for a disposable local development database only. Demo seeding is disabled by default and must never be enabled in a deployed environment. Change or remove demo credentials before any shared or production deployment.

### End-to-end smoke test

1. Sign in as `demo`.
2. Open Documents and request read access to the seeded confidential research proposal.
3. Open Decisions and confirm an ALLOW decision was recorded.
4. Open Audit history and confirm an ACCESS_ALLOW event exists.
5. Sign out and sign in as `admin`.
6. Open People & roles and revoke the demo user's READ grant.
7. Sign out and sign back in as `demo`.
8. Request the same document again. The backend should deny the next request and persist a DENY decision and audit event.

### Current implementation boundaries

- The document endpoint protects and returns document metadata plus the decision record. Binary file storage and streaming through a dedicated document gateway are not yet implemented.
- Policy Sandbox, draft policy simulation, immutable policy publication, PIV, and historical replay remain planned/prototype capabilities.
- Audit events are hash-linked in the database. External signed checkpoints and independent key custody are future hardening work; a database hash chain is not an absolute immutability guarantee.
- Run the CI workflow and local tests before claiming the branch is verified. A workflow file or committed test is not proof that a test run passed.
