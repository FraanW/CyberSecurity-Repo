#!/bin/bash
# Boot Keycloak on Render (free tier). Substitutes the real public URLs into the
# realm templates, tunes the JVM for 512 MB, and starts in dev mode with import.
set -e

# Render injects RENDER_EXTERNAL_URL = this service's public https URL.
# Override with PUBLIC_ORIGIN if you want. CLIENT_ORIGIN is the static client's
# URL (set it once the client is deployed; defaults to PUBLIC_ORIGIN meanwhile).
PUBLIC_ORIGIN="${PUBLIC_ORIGIN:-${RENDER_EXTERNAL_URL}}"
CLIENT_ORIGIN="${CLIENT_ORIGIN:-${PUBLIC_ORIGIN}}"

if [ -z "${PUBLIC_ORIGIN}" ]; then
  echo "FATAL: PUBLIC_ORIGIN (or RENDER_EXTERNAL_URL) is not set." >&2
  exit 1
fi
echo "Keycloak public origin : ${PUBLIC_ORIGIN}"
echo "Client origin          : ${CLIENT_ORIGIN}"

# Substitute placeholders in each realm template -> data/import (bash, no sed dep)
mkdir -p /opt/keycloak/data/import
for f in /opt/keycloak/data/import-templates/*.json; do
  out="/opt/keycloak/data/import/$(basename "$f")"
  content="$(cat "$f")"
  content="${content//__KC_ORIGIN__/${PUBLIC_ORIGIN}}"
  content="${content//__CLIENT_ORIGIN__/${CLIENT_ORIGIN}}"
  printf '%s' "${content}" > "${out}"
  echo "prepared realm: $(basename "$f")"
done

# Render provides the port to listen on via $PORT; Keycloak reads KC_HTTP_PORT.
export KC_HTTP_PORT="${PORT:-8080}"
# Public hostname + proxy awareness (Render terminates TLS, forwards HTTP).
export KC_HOSTNAME="${PUBLIC_ORIGIN}"
export KC_HTTP_ENABLED=true
export KC_PROXY_HEADERS=xforwarded
# Fit the JVM inside 512 MB (free/starter). Bump if you move to Standard (2 GB).
export JAVA_OPTS_APPEND="${JAVA_OPTS_APPEND:--Xms128m -Xmx400m}"

# Admin bootstrap creds. Default to admin/admin (LAB ONLY) so the service boots
# even if the env vars are unset or mistyped. Both must be set together, or
# Keycloak refuses to start ("bootstrap-admin-username available only when
# bootstrap admin password is set"). Override both via env for anything real.
export KC_BOOTSTRAP_ADMIN_USERNAME="${KC_BOOTSTRAP_ADMIN_USERNAME:-admin}"
export KC_BOOTSTRAP_ADMIN_PASSWORD="${KC_BOOTSTRAP_ADMIN_PASSWORD:-admin}"

echo "Starting Keycloak on port ${KC_HTTP_PORT} ..."
exec /opt/keycloak/bin/kc.sh start-dev --import-realm
