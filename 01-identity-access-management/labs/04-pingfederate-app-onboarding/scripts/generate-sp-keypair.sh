#!/usr/bin/env bash
# Mints a stable SAML signing keypair for the SP app, and prints the two Render environment
# variables to paste in.
#
# Why you want this: with no keypair supplied, the app generates a throwaway one at startup.
# That is fine for a first look, but it changes on every restart — and PingFederate stops
# trusting your signed AuthnRequests the moment it does. A fixed keypair survives redeploys.
#
# Usage:  ./generate-sp-keypair.sh [common-name] [days]
# Output: sp-signing.key + sp-signing.crt in the current directory (both .gitignored).

set -euo pipefail

CN="${1:-pingfed-saml-sp-lab}"
DAYS="${2:-3650}"

if [[ -f sp-signing.key ]]; then
  echo "sp-signing.key already exists here. Move it aside first — overwriting it would break" >&2
  echo "any PingFederate connection that already trusts the matching certificate." >&2
  exit 1
fi

openssl req -x509 -newkey rsa:2048 -nodes \
  -keyout sp-signing.key \
  -out sp-signing.crt \
  -days "$DAYS" \
  -subj "/CN=${CN}/OU=IAM Lab/O=FinCo Lab" 2>/dev/null

chmod 600 sp-signing.key

echo "Created sp-signing.key and sp-signing.crt (valid ${DAYS} days)."
echo
echo "1. In Render → your saml-sp service → Environment, add these two variables."
echo "   Paste the whole block for each, BEGIN and END lines included."
echo
echo "--- LAB_SAML_SP_PRIVATE_KEY -------------------------------------------------"
cat sp-signing.key
echo "--- LAB_SAML_SP_CERTIFICATE -------------------------------------------------"
cat sp-signing.crt
echo "-----------------------------------------------------------------------------"
echo
echo "2. Redeploy, then re-download /api/sp-metadata.xml — it now carries this certificate."
echo "3. Keep sp-signing.key off git. The repo .gitignore already blocks *.key and *.crt."
