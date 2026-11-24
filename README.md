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
