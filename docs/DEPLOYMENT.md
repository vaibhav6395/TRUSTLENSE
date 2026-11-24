# Deployment guide

TRUSTLENS currently has a development backend and frontend. Hosting requires a public frontend origin, an HTTPS backend, and a managed/private PostgreSQL database. The repository does not contain production credentials and does not claim that a hosted deployment exists.

## Required environment

Backend:
- `DB_URL`: JDBC URL for managed PostgreSQL.
- `DB_USERNAME` / `DB_PASSWORD`: dedicated database credentials.
- `COOKIE_SECURE=true`: required behind HTTPS.
- `CORS_ALLOWED_ORIGINS`: exact public frontend origin, e.g. `https://your-frontend.example`.
- `DEMO_DATA_ENABLED=false`: always off for hosted deployments.
- `SERVER_PORT`: platform-provided port if required.

Frontend build:
- `VITE_API_BASE_URL`: HTTPS base URL of the backend API, without a trailing slash.

## Hosting checklist

1. Provision PostgreSQL with backups and network restrictions. Do not expose port 5432 to the public internet.
2. Deploy the Spring Boot backend to a platform that supports Java 21 or the provided Docker image.
3. Configure the backend variables above in the host's secret/environment settings.
4. Deploy the Vite frontend as a static site and set `VITE_API_BASE_URL` at build time.
5. Set `CORS_ALLOWED_ORIGINS` to the exact frontend origin.
6. Confirm HTTPS, secure session cookies, CSRF behavior, and cross-origin credential handling.
7. Run smoke tests with synthetic accounts and documents.
8. Keep demo seeding disabled; create real initial users through a controlled administrator provisioning path before any shared use.

## Current deployment limitations

- No production host, database, domain, or deployment credentials are configured by this repository.
- A public source repository does not automatically create a working public web application.
- The current backend does not yet include secure binary file storage/streaming, a production user-provisioning workflow, rate limiting, or full operational monitoring.
- Do not deploy this prototype for real confidential campus data until the remaining security work and an independent review are complete.
