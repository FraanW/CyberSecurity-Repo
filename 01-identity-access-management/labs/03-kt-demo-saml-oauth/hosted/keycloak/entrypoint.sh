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
# Single instance: use local caches, NOT clustered Infinispan. `cache` is a
# RUNTIME option (Keycloak ignores it at build time), so it must be set here.
# This avoids the JGroups cluster-formation churn that made boot take ~5 min and
# flooded the log with "Socket is closed" warnings — and it lowers memory.
export KC_CACHE=local

# JVM tuned to the MAX for Render free's 512 MB. We set the FULL JAVA_OPTS (not
# _APPEND) so we can switch to SerialGC: single GC thread, no G1 region bookkeeping
# — much lighter, and ideal for a 1-user service on 0.1 CPU. (Adding a GC via
# _APPEND fails with "Multiple garbage collectors selected", so we replace the lot.)
# We also cap the non-heap regions — code cache, direct memory, metaspace — to
# shrink RSS and leave the 256 MB heap room inside 512 MB. If it still OOMs under
# heavy interactive load, that's the 512 MB wall (→ Standard, 2 GB).
export JAVA_OPTS="-XX:+UseSerialGC -Xms32m -Xmx256m \
-XX:MaxMetaspaceSize=200m -XX:ReservedCodeCacheSize=48m -XX:MaxDirectMemorySize=48m \
-XX:+ExitOnOutOfMemoryError \
-Djava.net.preferIPv4Stack=true -Djava.awt.headless=true \
-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 \
-Duser.language=en -Duser.country=US"

# Admin bootstrap (LAB ONLY) — default so it boots with zero required env vars.
export KC_BOOTSTRAP_ADMIN_USERNAME="${KC_BOOTSTRAP_ADMIN_USERNAME:-admin}"
export KC_BOOTSTRAP_ADMIN_PASSWORD="${KC_BOOTSTRAP_ADMIN_PASSWORD:-admin}"

echo "Starting Keycloak (optimized) on port ${KC_HTTP_PORT} ..."
exec /opt/keycloak/bin/kc.sh start --optimized --import-realm
