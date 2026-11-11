# Lab 05 "banks" — one Keycloak holding two realms, each playing an external bank's IdP.
# Same optimized two-stage build as lab 03 (fits Render's free 512 MB).
# Realm JSONs are templates; entrypoint.sh fills in the real URLs at boot.

FROM quay.io/keycloak/keycloak:26.6.4 AS builder
# Build-time option baked into the optimized image. (cache is a RUNTIME option —
# it's set to `local` in entrypoint.sh, not here, or Keycloak ignores it.)
ENV KC_DB=dev-file
RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:26.6.4
# Take the pre-built (augmented) server from the builder stage
COPY --from=builder /opt/keycloak/ /opt/keycloak/
# Realm templates + the boot-time substitution script
COPY realms/ /opt/keycloak/data/import-templates/
COPY entrypoint.sh /opt/keycloak/entrypoint.sh

USER root
RUN chmod +x /opt/keycloak/entrypoint.sh
USER 1000

ENTRYPOINT ["/opt/keycloak/entrypoint.sh"]
