#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

ADMIN_JAR="$TMP_DIR/admin.cookies"
DEMO_JAR="$TMP_DIR/demo.cookies"
: > "$ADMIN_JAR"
: > "$DEMO_JAR"
PDF="$TMP_DIR/synthetic.pdf"
printf '%%PDF-1.7\nTRUSTLENS synthetic security test\n' > "$PDF"

csrf_token() {
  local jar="$1"
  curl --fail --silent --show-error -b "$jar" -c "$jar" "$BASE_URL/api/v1/auth/csrf" | jq -r '.token'
}

login() {
  local jar="$1" username="$2" password="$3" token="$4"
  curl --fail --silent --show-error -b "$jar" -c "$jar" \
    -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $token" \
    -d "$(jq -n --arg u "$username" --arg p "$password" '{username:$u,password:$p}')" \
    "$BASE_URL/api/v1/auth/login" | jq -e '.authenticated == true' >/dev/null
}

ADMIN_CSRF="$(csrf_token "$ADMIN_JAR")"
login "$ADMIN_JAR" admin "$DEMO_ADMIN_PASSWORD" "$ADMIN_CSRF"

UPLOAD_JSON="$(curl --fail --silent --show-error -b "$ADMIN_JAR" -c "$ADMIN_JAR" \
  -H "X-XSRF-TOKEN: $ADMIN_CSRF" \
  -F "file=@$PDF;type=application/pdf" \
  -F "title=Revocation smoke test" \
  -F "classification=CONFIDENTIAL" \
  "$BASE_URL/api/v1/documents")"
DOCUMENT_ID="$(jq -r '.id' <<<"$UPLOAD_JSON")"
test -n "$DOCUMENT_ID" && test "$DOCUMENT_ID" != "null"

GRANT_EXPIRY="$(date -u -d '+1 hour' '+%Y-%m-%dT%H:%M:%SZ')"
GRANT_JSON="$(jq -n --arg id "$DOCUMENT_ID" --arg expiry "$GRANT_EXPIRY" \
  '{documentId:$id,subjectUsername:"demo",permission:"DOWNLOAD",expiresAt:$expiry}')"
create_grant() {
  local permission="$1"
  local payload response
  payload="$(jq -n --arg id "$DOCUMENT_ID" --arg expiry "$GRANT_EXPIRY" --arg permission "$permission" \
    '{documentId:$id,subjectUsername:"demo",permission:$permission,expiresAt:$expiry}')"
  response="$(curl --fail --silent --show-error -b "$ADMIN_JAR" -c "$ADMIN_JAR" \
    -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $ADMIN_CSRF" \
    -d "$payload" "$BASE_URL/api/v1/grants")"
  jq -r '.grantId' <<<"$response"
}
READ_GRANT_ID="$(create_grant READ)"
DOWNLOAD_GRANT_ID="$(create_grant DOWNLOAD)"
test -n "$READ_GRANT_ID" && test "$READ_GRANT_ID" != "null"
test -n "$DOWNLOAD_GRANT_ID" && test "$DOWNLOAD_GRANT_ID" != "null"

DEMO_CSRF="$(csrf_token "$DEMO_JAR")"
login "$DEMO_JAR" demo "$DEMO_FACULTY_PASSWORD" "$DEMO_CSRF"

INITIAL_READ_STATUS="$(curl --silent --show-error -b "$DEMO_JAR" -o "$TMP_DIR/metadata.json" \
  -w '%{http_code}' "$BASE_URL/api/v1/documents/$DOCUMENT_ID")"
test "$INITIAL_READ_STATUS" = "200"

INITIAL_STATUS="$(curl --silent --show-error -b "$DEMO_JAR" -o "$TMP_DIR/allowed.pdf" \
  -w '%{http_code}' "$BASE_URL/api/v1/documents/$DOCUMENT_ID/content")"
test "$INITIAL_STATUS" = "200"

curl --fail --silent --show-error -b "$ADMIN_JAR" -c "$ADMIN_JAR" \
  -H "X-XSRF-TOKEN: $ADMIN_CSRF" -X POST \
  "$BASE_URL/api/v1/grants/$READ_GRANT_ID/revoke" | jq -e '.revoked == true' >/dev/null
curl --fail --silent --show-error -b "$ADMIN_JAR" -c "$ADMIN_JAR" \
  -H "X-XSRF-TOKEN: $ADMIN_CSRF" -X POST \
  "$BASE_URL/api/v1/grants/$DOWNLOAD_GRANT_ID/revoke" | jq -e '.revoked == true' >/dev/null

READ_RETRY_STATUS="$(curl --silent --show-error -b "$DEMO_JAR" -o "$TMP_DIR/denied-read.json" \
  -w '%{http_code}' "$BASE_URL/api/v1/documents/$DOCUMENT_ID")"
test "$READ_RETRY_STATUS" = "403"

RETRY_STATUS="$(curl --silent --show-error -b "$DEMO_JAR" -o "$TMP_DIR/denied.txt" \
  -w '%{http_code}' "$BASE_URL/api/v1/documents/$DOCUMENT_ID/content")"
test "$RETRY_STATUS" = "403"

DECISION="$(curl --fail --silent --show-error -b "$DEMO_JAR" -c "$DEMO_JAR" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $DEMO_CSRF" \
  -d '{"action":"DOWNLOAD"}' "$BASE_URL/api/v1/documents/$DOCUMENT_ID/access-check")"
jq -e '.allowed == false and .reasonCode == "GRANT_REVOKED"' <<<"$DECISION" >/dev/null

echo "PASS: active session retained; revoked READ and DOWNLOAD grants block metadata API and reused file URL (HTTP 403); denial reason GRANT_REVOKED recorded."
