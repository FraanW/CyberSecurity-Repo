# Keycloak for Render (free tier) — SAML IdP + OAuth/OIDC Authorization Server
# for the reverse-KT demo. Realm JSONs are templated (__KC_ORIGIN__ /
# __CLIENT_ORIGIN__) and substituted at boot by entrypoint.sh, so this image
# is portable to whatever *.onrender.com hostname Render assigns.

FROM quay.io/keycloak/keycloak:26.6.4

# Realm templates (with placeholder URLs) + the boot-time substitution script
COPY realms/ /opt/keycloak/data/import-templates/
COPY entrypoint.sh /opt/keycloak/entrypoint.sh

USER root
RUN chmod +x /opt/keycloak/entrypoint.sh
USER 1000

ENTRYPOINT ["/opt/keycloak/entrypoint.sh"]
