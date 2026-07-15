#!/usr/bin/env bash
# OAuth 2.0 grant-type demos against the KT lab (Keycloak realm: finco-idp)
# Bash + curl. Decodes JWTs with python3 (falls back to raw if absent).
# Start the stack first:  docker compose up -d
#
# Usage:
#   ./oauth-demos.sh client-credentials   # Demo C — machine-to-machine, no user
#   ./oauth-demos.sh device               # Demo D — "go to URL, enter code"
#   ./oauth-demos.sh refresh              # Demo E — renew an access token silently
#   ./oauth-demos.sh ropc                 # BONUS — the DEPRECATED password grant

set -euo pipefail
REALM="http://localhost:8080/realms/finco-idp/protocol/openid-connect"

jwt() {   # decode the header+payload of a JWT passed on stdin arg
  local t="$1"
  if command -v python3 >/dev/null 2>&1; then
    python3 - "$t" <<'PY'
import sys, base64, json
tok = sys.argv[1]
for part in tok.split('.')[:2]:
    part += '=' * (-len(part) % 4)
    print(json.dumps(json.loads(base64.urlsafe_b64decode(part)), indent=2))
    print('---')
PY
  else echo "(install python3 or jq to decode; raw token: $t)"; fi
}

case "${1:-}" in

client-credentials)
  echo "=== Demo C · CLIENT CREDENTIALS (machine-to-machine) ==="
  echo "No browser, no /authorize, no user. The service authenticates as itself."
  RESP=$(curl -s -X POST "$REALM/token" \
    -d grant_type=client_credentials -d client_id=kt-service -d client_secret=kt-service-secret)
  echo "$RESP" | python3 -m json.tool 2>/dev/null || echo "$RESP"
  echo "--- access token (decoded) ---"
  jwt "$(echo "$RESP" | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])')"
  echo "Note: no refresh_token (a service just re-authenticates); azp = kt-service, not a person."
  ;;

device)
  echo "=== Demo D · DEVICE AUTHORIZATION (smart TV / CLI) ==="
  D=$(curl -s -X POST "$REALM/auth/device" -d client_id=kt-device -d scope="openid profile")
  URI=$(echo "$D" | python3 -c 'import sys,json;print(json.load(sys.stdin)["verification_uri"])')
  CODE=$(echo "$D" | python3 -c 'import sys,json;print(json.load(sys.stdin)["user_code"])')
  DC=$(echo "$D" | python3 -c 'import sys,json;print(json.load(sys.stdin)["device_code"])')
  IV=$(echo "$D" | python3 -c 'import sys,json;print(json.load(sys.stdin)["interval"])')
  echo ""
  echo "  On your phone/browser, go to: $URI"
  echo "  and enter this code:          $CODE"
  echo ""
  echo "  Polling every ${IV}s while you approve..."
  while true; do
    sleep "$IV"
    T=$(curl -s -X POST "$REALM/token" \
      -d grant_type=urn:ietf:params:oauth:grant-type:device_code -d device_code="$DC" -d client_id=kt-device)
    if echo "$T" | grep -q '"access_token"'; then
      echo ""; echo "Approved! Tokens issued:"; echo "$T" | python3 -m json.tool
      echo "--- id token (decoded) ---"
      jwt "$(echo "$T" | python3 -c 'import sys,json;print(json.load(sys.stdin)["id_token"])')"
      break
    fi
    ERR=$(echo "$T" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("error",""))')
    if [ "$ERR" = "authorization_pending" ] || [ "$ERR" = "slow_down" ]; then printf "."; continue; fi
    echo ""; echo "Stopped: $ERR"; break
  done
  ;;

refresh)
  echo "=== Demo E · REFRESH TOKEN (silent renewal) ==="
  FIRST=$(curl -s -X POST "$REALM/token" \
    -d grant_type=password -d client_id=kt-web -d client_secret=kt-web-secret \
    -d username=farhaan -d password='Passw0rd!' -d scope=openid)
  RT1=$(echo "$FIRST" | python3 -c 'import sys,json;print(json.load(sys.stdin)["refresh_token"])')
  echo "Got an initial refresh_token (first 24): ${RT1:0:24}..."
  sleep 1
  SECOND=$(curl -s -X POST "$REALM/token" \
    -d grant_type=refresh_token -d client_id=kt-web -d client_secret=kt-web-secret -d refresh_token="$RT1")
  RT2=$(echo "$SECOND" | python3 -c 'import sys,json;print(json.load(sys.stdin)["refresh_token"])')
  echo "Refreshed. NEW access token issued with no user interaction."
  echo "Old refresh_token first 24: ${RT1:0:24}..."
  echo "New refresh_token first 24: ${RT2:0:24}...  <- rotated (different)"
  ;;

ropc)
  echo "=== BONUS · RESOURCE OWNER PASSWORD CREDENTIALS (DEPRECATED) ==="
  echo "The app collects the user's ACTUAL password. This is the anti-pattern OAuth"
  echo "exists to kill: the app sees the password, and MFA is bypassed."
  curl -s -X POST "$REALM/token" \
    -d grant_type=password -d client_id=kt-web -d client_secret=kt-web-secret \
    -d username=farhaan -d password='Passw0rd!' -d scope=openid | python3 -m json.tool
  echo "It works (enabled for this demo) — and THAT is the problem. Removed in OAuth 2.1."
  ;;

*)
  echo "Usage: $0 {client-credentials|device|refresh|ropc}"; exit 1 ;;
esac
