# TRUSTLENS

**Explainable, continuously enforced access governance for campus document exchange.**

TRUSTLENS is a security-focused prototype for campus document access. It combines server-side sessions, deny-by-default authorization, role and contextual checks, grant revocation, explainable decisions, and hash-linked audit events. The roadmap extends this foundation with policy simulation, immutable policy publication, Policy Impact Verification (PIV), and historical decision replay.

> **Status:** Hackathon prototype / development foundation. Not production-ready. Binary document storage and streaming, complete ABAC policy authoring, immutable policy publication, PIV, historical replay, and independent audit checkpoints are not yet complete.

## Repository layout

- `backend/` — Java 21, Spring Boot, Spring Security, PostgreSQL
- `frontend/` — React, TypeScript, Vite
- `database/` — database notes
- `api-contract/` — OpenAPI contract
- `docs/` — architecture, threat model, setup, deployment, roadmap
- `security-tests/` — security test plan

## Implemented foundation

- Database-backed login/logout with BCrypt and server-side sessions.
- CSRF token bootstrap and session ID rotation on login.
- Role-based identities and server-derived actor context.
- Deny-by-default document authorization with owner, public-read, active-grant, account, document-state, and same-department security-admin checks.
- Persistent ALLOW/DENY decisions with reason codes and evidence snapshots.
- Security-admin grant listing and revocation.
- Hash-linked audit events for access decisions and grant revocation.
- React dashboard for sign-in, documents, decisions, audit history, and grant administration.
- Automated backend and frontend tests plus dependency audit checks.

## Planned / prototype roadmap

1. Complete secure document storage and authenticated file delivery.
2. Generalize authorization into configurable RBAC + ABAC policies.
3. Build a read-only Policy Sandbox with synthetic scenario simulation and impact visualization.
4. Add immutable policy versions and controlled publication/approval.
5. Implement Policy Impact Verification (predicted versus observed outcomes).
6. Add historical decision replay using original policy and evidence snapshots.
7. Harden audit verification with signed external checkpoints.
8. Add advisory anomaly detection and administrator analytics.

See [docs/FEATURE_ROADMAP.md](docs/FEATURE_ROADMAP.md) for status details and [docs/architecture.md](docs/architecture.md) for design boundaries.

## Local development

Prerequisites: Git, Java 21, Maven, Node.js 20+, npm, and Docker Compose.

1. Create local environment configuration:

```bash
cp .env.example .env
```

Change every sample password in `.env` before use. The `.env` file is ignored by Git.

2. Start PostgreSQL:

```bash
docker compose up -d db
```

3. Start the backend. For synthetic demo data, provide unique passwords locally:

Kali/Linux/macOS:

```bash
cd backend
set -a
source ../.env
set +a
DEMO_DATA_ENABLED=true COOKIE_SECURE=false mvn spring-boot:run
```

PowerShell:

```powershell
cd backend
$env:DB_URL = "jdbc:postgresql://localhost:5432/trustlens"
$env:DB_USERNAME = "trustlens"
$env:DB_PASSWORD = "the-same-value-as-POSTGRES_PASSWORD-in-your-local-env-file"
$env:DEMO_DATA_ENABLED = "true"
$env:DEMO_FACULTY_PASSWORD = "your-unique-password-at-least-14-characters"
$env:DEMO_ADMIN_PASSWORD = "a-different-unique-password-at-least-14-characters"
$env:COOKIE_SECURE = "false"
mvn spring-boot:run
```

4. In another terminal:

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5173. Replace all placeholders in `.env` first; the file is ignored by Git.

Demo seeding is disabled by default. It requires both environment passwords (minimum 14 characters). Use only a disposable local database and synthetic data. Never enable demo seeding in a shared or deployed environment.

## Security configuration

- Set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` to deployment-specific values.
- Set `COOKIE_SECURE=true` behind HTTPS.
- Set `CORS_ALLOWED_ORIGINS` to the exact frontend origin(s), comma-separated. Do not use a wildcard with credentialed sessions.
- Keep `DEMO_DATA_ENABLED=false` in all shared/deployed environments.
- Store secrets in the deployment platform's secret manager, not in Git.
- Use HTTPS and a managed PostgreSQL service for hosted environments.
- Do not expose PostgreSQL publicly.
- Use synthetic identities and documents in demos.

See [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md).

## Verification

Run backend tests:

```bash
cd backend && mvn test
```

Run frontend checks:

```bash
cd frontend && npm ci && npm test && npm run build && npm audit --audit-level=high
```

A successful build does not by itself establish production security. Review [docs/threat-model.md](docs/threat-model.md) and [security-tests/README.md](security-tests/README.md).

## License

No license has been selected yet. Until a license is added, do not assume that the code is available for reuse under an open-source license.
