<#
.SYNOPSIS
  Mints a stable SAML signing keypair for the SP app (Windows 11 / PowerShell).

.DESCRIPTION
  With no keypair supplied, the app generates a throwaway one at startup. That is fine for a
  first look, but it changes on every restart — and PingFederate stops trusting your signed
  AuthnRequests the moment it does. A fixed keypair survives redeploys.

  Needs openssl on PATH. Git for Windows ships one at
  C:\Program Files\Git\usr\bin\openssl.exe

.EXAMPLE
  .\Generate-SpKeypair.ps1
  .\Generate-SpKeypair.ps1 -CommonName "pingfed-saml-sp-lab" -Days 3650
#>
param(
    [string]$CommonName = "pingfed-saml-sp-lab",
    [int]$Days = 3650
)

$ErrorActionPreference = "Stop"

if (Test-Path "sp-signing.key") {
    Write-Error "sp-signing.key already exists here. Move it aside first — overwriting it would break any PingFederate connection that already trusts the matching certificate."
}

openssl req -x509 -newkey rsa:2048 -nodes `
    -keyout sp-signing.key `
    -out sp-signing.crt `
    -days $Days `
    -subj "/CN=$CommonName/OU=IAM Lab/O=FinCo Lab"

Write-Host "Created sp-signing.key and sp-signing.crt (valid $Days days)." -ForegroundColor Green
Write-Host ""
Write-Host "1. In Render -> your saml-sp service -> Environment, add these two variables."
Write-Host "   Paste the whole block for each, BEGIN and END lines included."
Write-Host ""
Write-Host "--- LAB_SAML_SP_PRIVATE_KEY -------------------------------------------------"
Get-Content sp-signing.key
Write-Host "--- LAB_SAML_SP_CERTIFICATE -------------------------------------------------"
Get-Content sp-signing.crt
Write-Host "-----------------------------------------------------------------------------"
Write-Host ""
Write-Host "2. Redeploy, then re-download /api/sp-metadata.xml - it now carries this certificate."
Write-Host "3. Keep sp-signing.key off git. The repo .gitignore already blocks *.key and *.crt."
