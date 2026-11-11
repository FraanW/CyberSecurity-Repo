#!/bin/bash
# Boot the two-bank Keycloak. Fills the real URLs into the realm templates, then
# starts Keycloak in optimized mode with realm import. Stateless: every restart
# re-imports the realms, so a changed PF_PUBLIC_URL takes effect on restart.
set -e

PUBLIC_ORIGIN="${PUBLIC_ORIGIN:-${RENDER_EXTERNAL_URL}}"
PUBLIC_ORIGIN="${PUBLIC_ORIGIN%/}"
if [ -z "${PUBLIC_ORIGIN}" ]; then
  echo "FATAL: PUBLIC_ORIGIN (or RENDER_EXTERNAL_URL) is not set." >&2
  exit 1
fi

# Where our PingFederate runtime is reachable (the tunnel URL). The banks POST the
# SAML response to ${PF_PUBLIC_URL}/sp/ACS.saml2.
PF_PUBLIC_URL="${PF_PUBLIC_URL:-https://pf-not-configured.invalid}"
PF_PUBLIC_URL="${PF_PUBLIC_URL%/}"
# PingFederate's SAML 2.0 entity ID (System > Server > Protocol Settings > Federation Info).
PF_SP_ENTITY_ID="${PF_SP_ENTITY_ID:-lab05:finco:pingfederate:sp}"
# Commerce Control's public URL. Only Bank A uses it (as its RelayState).
APP_ORIGIN="${APP_ORIGIN:-https://app-not-configured.invalid}"
APP_ORIGIN="${APP_ORIGIN%/}"
# One shared password for the dummy bank users (LAB ONLY).
LAB_USER_PASSWORD="${LAB_USER_PASSWORD:-Passw0rd!}"

echo "Keycloak public origin : ${PUBLIC_ORIGIN}"
echo "PingFederate ACS       : ${PF_PUBLIC_URL}/sp/ACS.saml2"
echo "PingFederate entity ID : ${PF_SP_ENTITY_ID}"
echo "Commerce Control       : ${APP_ORIGIN}"

mkdir -p /opt/keycloak/data/import
for f in /opt/keycloak/data/import-templates/*.json; do
  out="/opt/keycloak/data/import/$(basename "$f")"
  content="$(cat "$f")"
  content="${content//__PF_ORIGIN__/${PF_PUBLIC_URL}}"
  content="${content//__PF_SP_ENTITY_ID__/${PF_SP_ENTITY_ID}}"
  content="${content//__APP_ORIGIN__/${APP_ORIGIN}}"
  content="${content//__LAB_USER_PASSWORD__/${LAB_USER_PASSWORD}}"
  printf '%s' "${content}" > "${out}"
  echo "prepared realm: $(basename "$f")"
done

export KC_HTTP_PORT="${PORT:-8080}"
export KC_HOSTNAME="${PUBLIC_ORIGIN}"
export KC_HOSTNAME_STRICT=false
export KC_HTTP_ENABLED=true
export KC_PROXY_HEADERS=xforwarded
export KC_CACHE=local

# Same memory tuning as lab 03: SerialGC and capped non-heap regions to fit 512 MB.
export JAVA_OPTS="-XX:+UseSerialGC -Xms32m -Xmx256m \
-XX:MaxMetaspaceSize=200m -XX:ReservedCodeCacheSize=48m -XX:MaxDirectMemorySize=48m \
-XX:+ExitOnOutOfMemoryError \
-Djava.net.preferIPv4Stack=true -Djava.awt.headless=true \
-Dfile.encoding=UTF-8 -Duser.language=en -Duser.country=US"

export KC_BOOTSTRAP_ADMIN_USERNAME="${KC_BOOTSTRAP_ADMIN_USERNAME:-admin}"
export KC_BOOTSTRAP_ADMIN_PASSWORD="${KC_BOOTSTRAP_ADMIN_PASSWORD:-admin}"

echo "Starting Keycloak (optimized) on port ${KC_HTTP_PORT} ..."
exec /opt/keycloak/bin/kc.sh start --optimized --import-realm
