# TRUSTLENS — local hackathon runbook

This setup runs the complete application locally with Docker Compose: PostgreSQL, the Spring Boot API, the React frontend, and persistent local volumes for the demo database and uploaded files.

> **Demo-only profile.** It deliberately enables synthetic demo users and uses HTTP with non-Secure session cookies so the app works at localhost. Do not expose this stack to the internet, use real student records, or reuse these credentials elsewhere.

## 1. Requirements

- Docker Engine
- Docker Compose v2 (`docker compose version`)
- Git

On Kali/Debian, install Docker Engine and the Compose plugin using the official Docker installation instructions for your distribution. Confirm both commands work before continuing.

## 2. Get the code

```bash
git clone https://github.com/vaibhav6395/TRUSTLENSE.git
cd TRUSTLENSE
git switch main
git pull --ff-only origin main
```

## 3. Create local secrets

```bash
cp .env.example .env
```

Edit `.env` and replace all three placeholders with unique values:
- `POSTGRES_PASSWORD`: local database password
- `DB_PASSWORD`: must match `POSTGRES_PASSWORD` (kept for compatibility with manual backend startup)
- `DEMO_FACULTY_PASSWORD`: at least 14 characters
- `DEMO_ADMIN_PASSWORD`: a different password, at least 14 characters

Keep `.env` on your machine. Never commit it or share screenshots that reveal its contents. The Compose stack passes the database password to the backend directly; the local demo does not need you to publish PostgreSQL or the API ports.

## 4. Build and start everything

```bash
docker compose -f docker-compose.demo.yml up --build -d
docker compose -f docker-compose.demo.yml ps
```

The first build may take several minutes while Maven and npm dependencies are downloaded. The frontend is bound to loopback only.

Open **http://localhost:5173**.

## 5. Demo sign-in

| Role | Username | Password |
|---|---|---|
| Faculty | `demo` | Value of `DEMO_FACULTY_PASSWORD` in your local `.env` |
| Security administrator | `admin` | Value of `DEMO_ADMIN_PASSWORD` in your local `.env` |

There are no shared default passwords. If the database volume already contains these users, changing the environment values will not reset their existing passwords.

## 6. Check the app

```bash
curl -i http://localhost:5173/api/v1/health
docker compose -f docker-compose.demo.yml logs --tail=100 backend
```

The health endpoint should return HTTP 200. For the grant-revocation demonstration, sign in as admin, upload a synthetic PDF, create READ and DOWNLOAD grants for `demo`, sign in as `demo` in another browser/private window, verify access, revoke both grants as admin, then retry the same document requests as `demo`. The next protected request should be denied.

For the automated revocation test, the script requires `curl` and `jq` on the host. Run it against a backend port-forward only if you deliberately expose the API for testing; the default demo Compose stack keeps that port private. The CI workflow runs this test against its own disposable test environment.

## 7. Stop and reset

Stop containers but keep demo data:

```bash
docker compose -f docker-compose.demo.yml down
```

Reset the disposable demo database and uploaded files (this permanently deletes them):

```bash
docker compose -f docker-compose.demo.yml down -v
```

Then rebuild with `up --build -d`.

## Troubleshooting

- **Compose command missing:** install the Docker Compose v2 plugin; check `docker compose version`.
- **Port 5173 is busy:** stop the other local service using that port before starting this stack.
- **Backend keeps restarting:** inspect `docker compose -f docker-compose.demo.yml logs backend`; confirm the three required passwords are set in `.env`.
- **Login returns 401:** use the passwords currently stored in the existing database; editing `.env` does not reset an existing account.
- **Login/API returns 403:** use the same origin `http://localhost:5173`, clear stale site cookies, and retry. Do not disable CSRF protection.
- **Database schema error:** inspect the database/backend logs. Flyway applies migrations automatically on startup.

## Scope

This runbook provides a reproducible local hackathon demonstration, not production deployment. Institutional SSO/MFA, managed encryption keys, malware scanning, rate limiting, monitoring, backup/restore exercises, load testing and independent security review remain separate release requirements.
