# Security test plan

Start with negative authorization tests before adding advanced features.

- Unauthenticated request to a protected endpoint returns 401.
- Authenticated user without a matching grant receives 403.
- User cannot change their effective role/department by editing request JSON.
- Revoked and expired grants are denied on the next request.
- A document identifier from another user cannot bypass authorization.
- A user cannot access a retired document.
- Non-admin cannot publish policy versions.
- Simulation makes no persistent policy changes.
- Logout invalidates the server session.
- Invalid input does not expose stack traces or internal configuration.

Tests must use synthetic users and documents.
