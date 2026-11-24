# Architecture

TRUSTLENS is a modular monolith with independently developed frontend and backend applications.

```text
React UI
  |
  | HTTPS / JSON
  v
Spring Boot controllers
  |
  v
Application services
  |--------------------|
  v                    v
Authorization service  Audit service
  |                    |
  v                    v
Policy evaluator       Audit events
  |                    |
  +---------+----------+
            v
       PostgreSQL
```

## Authorization request path

1. Authenticate the request using a server-side session.
2. Resolve the actor from the authenticated security context.
3. Load the target resource and current policy/grant state.
4. Evaluate role, department, classification, action, account status, grant expiry, and revocation.
5. Default to deny when required evidence is absent or evaluation fails.
6. Persist a decision explanation and policy version for traceability.
7. Deliver protected content only after the server-side decision allows it.

The client must not supply trusted actor identity, role, department, classification, or policy version.

## Revocation semantics

Once a grant is revoked, subsequent server-mediated requests must be denied. Revocation cannot retract content already downloaded, copied, or remembered.

## Policy simulation and publication

Simulation is read-only and reports predicted outcomes against a defined snapshot. Publication creates a new immutable version; historical decision records retain the policy version and evidence used at decision time.

## Audit integrity

A hash chain can reveal modification or deletion within a sequence, but does not make the database impossible to alter. Production-grade assurance requires protected signing keys and independently stored signed checkpoints.
