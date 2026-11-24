# Deployment guide

TRUSTLENS includes a containerized single-origin deployment prototype. It is not hosted by this repository yet: a public server/domain and TLS reverse proxy still need to be provisioned by the project owner.

## Deploy on a Linux host

Prerequisites:
- A Linux VM or container host with Docker Engine and Compose v2.
- A DNS name pointed at that host.
- An HTTPS reverse proxy (Caddy, Nginx, or a managed platform TLS proxy).
- A strong, unique PostgreSQL password stored as a server environment secret.

1. Clone the public repository after its visibility is changed:

```bash
git clone --single-branch --branch main https://github.com/vaibhav6395/TRUSTLENSE.git
cd TRUSTLENSE
```

2. Configure deployment variables in the host's secret store or a protected environment file outside the repository:

```bash
export POSTGRES_PASSWORD='use-a-unique-long-random-secret'
export PUBLIC_APP_ORIGIN='https://your-domain.example'
```

Do not commit these values. `PUBLIC_APP_ORIGIN` must exactly match the public HTTPS origin.

3. Build and start the stack:

```bash
docker compose -f docker-compose.production.yml up --build -d
docker compose -f docker-compose.production.yml ps
```

The web container listens on `127.0.0.1:8088`. Configure the host's HTTPS reverse proxy to forward the public domain to that local port. Do not expose PostgreSQL or the backend port publicly. The Compose database is private to the Docker network.

4. Verify:
- HTTPS certificate is valid.
- The site loads at the public domain.
- `/api/v1/health` returns the API health response through the same origin.
- Session cookies are Secure and HttpOnly.
- CSRF protection works for login and state-changing requests.
- `DEMO_DATA_ENABLED=false` remains in effect.
- No sample or local credentials are used.

## Configuration model

The production Compose file:
- Uses PostgreSQL 16 on an internal Docker network.
- Requires `POSTGRES_PASSWORD` at startup; there is no committed fallback.
- Runs the backend as a non-root user.
- Sets `COOKIE_SECURE=true`.
- Sets an exact `CORS_ALLOWED_ORIGINS` value.
- Keeps demo seeding disabled.
- Publishes only the frontend container to the host loopback interface.

## Hosted-platform alternative

A managed container platform can deploy the backend and frontend images separately, with managed PostgreSQL. Configure the same environment variables in the platform's secret manager. Set the frontend build variable `VITE_API_BASE_URL` to the HTTPS API base URL if frontend and API are hosted on different origins. In that case, configure credentialed CORS with the exact frontend origin and validate cookie SameSite behavior for the chosen domain arrangement.

## Current limitations and safety

- No production host, database, domain, or deployment credentials are configured by this repository; a live public application URL does not exist yet.
- The current backend does not yet include secure binary file storage/streaming, production user provisioning, rate limiting, or full operational monitoring.
- Do not deploy this prototype for real confidential campus data until the remaining security work and an independent review are complete.
- Use synthetic identities and documents only.
