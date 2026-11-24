# Team workflow

## Branches

- `develop`: shared integration branch
- `feature/backend-core`: backend/authentication/document delivery
- `feature/security-testing`: threat model and negative security tests
- `feature/frontend`: React UI and API client
- `feature/policy-intelligence`: policy simulation, versioning, impact analysis

Create feature branches from `develop`. Open pull requests into `develop`; do not commit directly to `main`. Pull before starting work and avoid editing the same files concurrently.

## Responsibilities

1. Backend: application foundation, persistence, sessions, authorization service, document API.
2. Security: threat model, OWASP-style test cases, access-control regression tests, review.
3. Frontend: dashboard, document views, policy sandbox UI, audit explorer.
4. Policy intelligence: policy model, simulation logic, version comparison, decision explanations.

## Integration contract

The OpenAPI document in `api-contract/openapi.yaml` is the API agreement. When a route or payload changes, update the contract and notify frontend/backend owners in the same pull request.

## Pull request checklist

- [ ] Scope and behavior described
- [ ] Tests added or updated
- [ ] No secrets, real student data, or production credentials
- [ ] Authorization checked on the server for every protected operation
- [ ] API contract updated if necessary
- [ ] Setup instructions remain accurate
