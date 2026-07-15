#!/bin/bash
# Boot the OPTIMIZED Keycloak on Render (free tier, 512 MB). Substitutes the
# real public URLs into the realm templates, tunes the JVM to stay under 512 MB,
# and starts in production-optimized mode (fast boot, low memory) with import.
set -e

# Render injects RENDER_EXTERNAL_URL = this service's public https URL.
PUBLIC_ORIGIN="${PUBLIC_ORIGIN:-${RENDER_EXTERNAL_URL}}"
CLIENT_ORIGIN="${CLIENT_ORIGIN:-${PUBLIC_ORIGIN}}"
# Strip any trailing slash(es) — a trailing "/" would produce malformed redirect
# URIs (".../*" -> "...//*") and an invalid CORS web origin.
PUBLIC_ORIGIN="${PUBLIC_ORIGIN%/}"
CLIENT_ORIGIN="${CLIENT_ORIGIN%/}"
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

# Runtime options (hostname/proxy). DB + cache are baked at build time (optimized).
export KC_HTTP_PORT="${PORT:-8080}"          # Render provides the port via $PORT
export KC_HOSTNAME="${PUBLIC_ORIGIN}"        # public https URL (issuer, redirects, SAML)
export KC_HOSTNAME_STRICT=false
export KC_HTTP_ENABLED=true                  # Render terminates TLS, forwards HTTP
export KC_PROXY_HEADERS=xforwarded           # trust X-Forwarded-* from Render's proxy

# JVM tuned for 512 MB: cap the heap at 256 MB (the base image otherwise defaults
# to -Xmx512m, which alone overruns the container). Do NOT set a GC here — the
# Keycloak image already selects one, and adding a second flag fails VM init with
# "Multiple garbage collectors selected". Optimized mode keeps class metadata small,
# so heap + metaspace + native fits under 512 MB.
export JAVA_OPTS_APPEND="${JAVA_OPTS_APPEND:--Xms64m -Xmx256m}"

# Admin bootstrap (LAB ONLY) — default so it boots with zero required env vars.
export KC_BOOTSTRAP_ADMIN_USERNAME="${KC_BOOTSTRAP_ADMIN_USERNAME:-admin}"
export KC_BOOTSTRAP_ADMIN_PASSWORD="${KC_BOOTSTRAP_ADMIN_PASSWORD:-admin}"

echo "Starting Keycloak (optimized) on port ${KC_HTTP_PORT} ..."
exec /opt/keycloak/bin/kc.sh start --optimized --import-realm
