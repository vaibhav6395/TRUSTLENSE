# Initial threat model

Scope: campus document exchange, accounts, grants, policy administration, audit records, and the web/API boundary.

| Threat | Required control | Verification |
|---|---|---|
| BOLA / IDOR | Check authorization against the requested document on every request | User A cannot read, download, update, or revoke User B's resource without permission |
| Privilege escalation | Derive roles/attributes from server-side identity and database state | Tampered client role/user fields do not change decisions |
| Stale access after revocation | Check current grant state for each protected request | Revoke grant, then repeat download request and expect denial |
| Session theft or fixation | Secure cookie settings, session rotation, logout invalidation | Inspect cookie flags and session lifecycle |
| CSRF | SameSite cookie policy plus CSRF protection for state-changing requests | Missing/invalid CSRF token is rejected |
| Insecure file delivery | Authenticated document gateway; no public storage links | Direct unauthenticated fetch is impossible |
| Policy tampering | Immutable published versions and restricted publication workflow | Published version cannot be silently edited |
| Audit manipulation | Append-only application behavior, hash chaining, external checkpoints later | Verify chain and detect altered event |
| Injection | Parameterized persistence, input validation, output encoding | Automated tests and dependency scanning |
| Secret leakage | Environment configuration; never commit credentials | Secret scanning and repository review |

## Demo safety

Use fabricated identities and synthetic documents only. Do not upload confidential student records or real access credentials.
