# TRUSTLENS identity and MFA workflow

## Account lifecycle

1. A faculty applicant submits username, institutional email, faculty identifier, department and password through `POST /api/v1/auth/signup`.
2. The request is stored in `faculty_signup_requests` with status `PENDING`. No row is created in `app_users`, so the applicant cannot authenticate.
3. A user with `ROLE_SECURITY_ADMIN` reviews the queue at `GET /api/v1/admin/signup-requests`.
4. Approval creates an enabled FACULTY account and records reviewer/time/status. Rejection records the decision without creating an account.
5. A new faculty member must enroll TOTP before the backend creates a full authenticated session.

Institutional identity checks are currently a manual administrator process. The app does not claim that an email address or faculty identifier has been independently verified by a campus directory.

## First administrator

In the production profile, `BootstrapAdminInitializer` creates the first SECURITY_ADMIN only when no active administrator role assignment exists. It requires:

- `BOOTSTRAP_ADMIN_USERNAME`
- `BOOTSTRAP_ADMIN_PASSWORD` (at least 16 characters)
- Optional display name and department

The password is BCrypt-hashed. The bootstrap never overwrites an existing administrator's password. There is no public administrator registration route.

## MFA protocol

- Password verification returns a 256-bit random challenge token. Only its SHA-256 hash is stored.
- The challenge is bound to the account, expires after five minutes and is single-use.
- A first-time user receives an ENROLL challenge. `POST /api/v1/auth/mfa/setup` creates a TOTP secret and an `otpauth://` provisioning URI.
- TOTP secrets are encrypted with AES-256-GCM. The 32-byte encryption key is provided by `MFA_ENCRYPTION_KEY`; it must remain stable and be backed up securely.
- `POST /api/v1/auth/mfa/verify` verifies a current TOTP code before creating the session.
- Enrollment returns ten random recovery codes once. Only SHA-256 hashes are persisted; each recovery code can be consumed once.
- Each challenge allows at most five code attempts. Password login is throttled after five failures in a 15-minute window per hashed username/remote-address key.

## Account disablement

SECURITY_ADMIN can list users and enable/disable accounts through `/api/v1/admin/users`. Self-disable is blocked, as is disabling the last active SECURITY_ADMIN. The existing session refresh filter reloads enabled status and roles from PostgreSQL on every authenticated request, so disabled accounts are rejected on the next request.

## Local development

Generate an MFA encryption key:

```bash
openssl rand -base64 32
```

Put the output in the private `.env` file as `MFA_ENCRYPTION_KEY`. Do not commit it or rotate it casually. If the key is lost, encrypted authenticator secrets cannot be decrypted; users will need an approved reset/re-enrollment process.

The local demo accounts are synthetic. Each must enroll MFA at first login. Use a real authenticator app and save the recovery codes in a secure location.

## API sequence

```text
POST /api/v1/auth/signup
  -> PENDING request

SECURITY_ADMIN:
GET  /api/v1/admin/signup-requests
POST /api/v1/admin/signup-requests/{id}/approve

Faculty:
POST /api/v1/auth/login
  -> { authenticated: false, mfaEnrollmentRequired: true, challengeToken }

POST /api/v1/auth/mfa/setup
  -> secret + otpauthUri

POST /api/v1/auth/mfa/verify
  -> authenticated session + one-time recovery codes
```

For an already-enrolled account, password login returns `mfaRequired: true` and a LOGIN challenge; the user skips setup and verifies TOTP or a recovery code.

## Remaining controls

Administrator-assisted MFA reset, campus SSO/directory integration, independently verified institutional email, signed external audit checkpoints and independent penetration testing remain release blockers for real campus data.
