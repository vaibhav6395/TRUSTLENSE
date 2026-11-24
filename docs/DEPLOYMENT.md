# Deployment guide

TRUSTLENSE currently includes a single-origin container deployment baseline. A Compose file is not, by itself, a production service: the institution must provide and operate the host, DNS, TLS reverse proxy, secret management, backups, monitoring and incident response.

## Current status

The repository is **not yet approved for real confidential institutional records**. See [PRODUCTION-READINESS.md](PRODUCTION-READINESS.md) for the phased implementation and outstanding controls. Use synthetic data until institutional security and privacy owners approve deployment.

## Target topology

- Institution-operated Linux host or approved container platform.
- HTTPS reverse proxy terminating TLS.
- Frontend published only on host loopback (127.0.0.1:8088) behind that proxy.
- Spring Boot backend and PostgreSQL on a private Docker network.
- Institution-controlled persistent volume for document files and database backups.
- Secrets supplied through the institution's secret manager or protected deployment environment, never committed to Git.

## Deploy the current baseline on a Linux host

Prerequisites:
- Docker Engine and Compose v2.
- A DNS name pointed at the host.
- An HTTPS reverse proxy (Caddy, Nginx, or an institution-managed TLS proxy).
- A strong unique PostgreSQL password stored as a deployment secret.
- Institution-approved persistent storage and backup locations.

1. Clone the repository:

```bash
git clone --single-branch --branch main https://github.com/vaibhav6395/TRUSTLENSE.git
cd TRUSTLENSE
```

2. Set deployment variables in the host's secret store or a protected environment file outside the repository:

```bash
export POSTGRES_PASSWORD='use-a-unique-long-random-secret'
export PUBLIC_APP_ORIGIN='https://trustlens.your-institution.example'
```

Do not commit these values. PUBLIC_APP_ORIGIN must exactly match the public HTTPS origin.

3. Build and start the stack:

```bash
docker compose -f docker-compose.production.yml config
docker compose -f docker-compose.production.yml up --build -d
docker compose -f docker-compose.production.yml ps
```

The web container listens on 127.0.0.1:8088. Configure the host's HTTPS reverse proxy to forward the public domain to that local port. Do not expose PostgreSQL or the backend port publicly.

4. Verify the baseline:
- HTTPS certificate is valid and renewed automatically.
- The site loads at the public domain.
- /api/v1/health returns the API health response through the same origin.
- Session cookies are Secure and HttpOnly.
- CSRF protection works for login and state-changing requests.
- The prod Spring profile is active and demo seeding is absent.
- Database and document volumes are persistent and backed up.
- No sample or local credentials are used.

## Current production profile protections

The production Compose service activates SPRING_PROFILES_ACTIVE=prod. The profile:
- Forces demo data seeding off.
- Uses Secure, HttpOnly session cookies with a 30-minute session timeout.
- Uses forwarded headers for the HTTPS reverse-proxy deployment.
- Suppresses exception details, stack traces and binding details in API error responses.

The demo initializer is also excluded whenever the prod profile is active, even if someone accidentally sets DEMO_DATA_ENABLED=true.

## Remaining release blockers

Before real institutional use, complete the controls in [PRODUCTION-READINESS.md](PRODUCTION-READINESS.md), including institutional identity provisioning/MFA, encryption at rest and key management, malware scanning/quarantine, rate limiting, operational monitoring, backup/restore exercises, authorization integration tests, load testing and independent security review.

A successful container start or health check is not a security certification.
