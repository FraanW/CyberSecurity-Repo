# Keycloak for Render (free tier) — SAML IdP + OAuth/OIDC Authorization Server.
# Two-stage OPTIMIZED build: `kc.sh build` bakes the augmentation at image-build
# time (baking DB=dev-file and a local, non-clustered cache). The running
# container then uses `start --optimized`, which is leaner (fits 512 MB) and
# boots far faster than dev mode. Realm JSONs are templated (__KC_ORIGIN__ /
# __CLIENT_ORIGIN__) and substituted at boot by entrypoint.sh.

FROM quay.io/keycloak/keycloak:26.6.4 AS builder
# Build-time options (baked into the optimized image):
ENV KC_DB=dev-file
ENV KC_CACHE=local
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
