# OAuth 2.0 grant-type demos against the KT lab (Keycloak realm: finco-idp)
# Windows PowerShell 5+ or PowerShell 7. Run the stack first:  docker compose up -d
#
# Dot-source this file to load the functions, then call them one at a time:
#   . .\oauth-demos.ps1
#   Show-ClientCredentials      # Demo C — machine-to-machine, no user
#   Show-DeviceFlow             # Demo D — "go to URL, enter code"
#   Show-Refresh                # Demo E — renew an access token silently
#   Show-Ropc                   # BONUS — the DEPRECATED password grant (why we don't use it)
#
# All flows print the raw token response AND decode the JWTs so the room sees inside.

$Realm = "http://localhost:8080/realms/finco-idp/protocol/openid-connect"

function Decode-Jwt($jwt) {
  if (-not $jwt) { return "(no token)" }
  ($jwt.Split('.')[0..1] | ForEach-Object {
    $s = $_.Replace('-','+').Replace('_','/')
    switch ($s.Length % 4) { 2 { $s += '==' } 3 { $s += '=' } }
    [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($s)) | ConvertFrom-Json | ConvertTo-Json -Depth 8
  }) -join "`n---`n"
}

# ---- Demo C: Client Credentials (no user; the app IS the identity) ----------
function Show-ClientCredentials {
  Write-Host "`n=== Demo C · CLIENT CREDENTIALS (machine-to-machine) ===" -ForegroundColor Cyan
  Write-Host "No browser, no /authorize, no user. The service authenticates as itself." -ForegroundColor DarkGray
  $r = Invoke-RestMethod -Method Post -Uri "$Realm/token" -Body @{
    grant_type    = 'client_credentials'
    client_id     = 'kt-service'
    client_secret = 'kt-service-secret'
  }
  $r | Format-List
  Write-Host "`n--- access token (decoded) ---" -ForegroundColor Yellow
  Decode-Jwt $r.access_token
  Write-Host "`nNote: NO refresh_token was issued (a service just re-authenticates). `azp`/`clientId` = kt-service, not a person." -ForegroundColor DarkGray
}

# ---- Demo D: Device Authorization Grant (input-constrained devices) ---------
function Show-DeviceFlow {
  Write-Host "`n=== Demo D · DEVICE AUTHORIZATION (smart TV / CLI) ===" -ForegroundColor Cyan
  $d = Invoke-RestMethod -Method Post -Uri "$Realm/auth/device" -Body @{ client_id = 'kt-device'; scope = 'openid profile' }
  Write-Host "`n  On your phone/browser, go to: " -NoNewline; Write-Host $d.verification_uri -ForegroundColor Green
  Write-Host "  and enter this code:        " -NoNewline; Write-Host $d.user_code -ForegroundColor Green
  Write-Host "  (or open directly: $($d.verification_uri_complete))" -ForegroundColor DarkGray
  Write-Host "`n  Polling the token endpoint every $($d.interval)s while you approve..." -ForegroundColor DarkGray
  $deadline = (Get-Date).AddSeconds([int]$d.expires_in)
  while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds ([int]$d.interval)
    try {
      $t = Invoke-RestMethod -Method Post -Uri "$Realm/token" -Body @{
        grant_type  = 'urn:ietf:params:oauth:grant-type:device_code'
        device_code = $d.device_code
        client_id   = 'kt-device'
      }
      Write-Host "`nApproved! Tokens issued:" -ForegroundColor Green
      $t | Format-List
      Write-Host "`n--- id token (decoded) ---" -ForegroundColor Yellow
      Decode-Jwt $t.id_token
      return
    } catch {
      $err = ($_.ErrorDetails.Message | ConvertFrom-Json -ErrorAction SilentlyContinue).error
      if ($err -eq 'authorization_pending' -or $err -eq 'slow_down') { Write-Host "." -NoNewline; continue }
      Write-Host "`nStopped: $err" -ForegroundColor Red; return
    }
  }
  Write-Host "`nDevice code expired before approval." -ForegroundColor Red
}

# ---- Demo E: Refresh Token (renew without re-login) -------------------------
# For a script we first get tokens via ROPC (kt-web) just to obtain a refresh_token,
# then exercise the refresh grant. In the LIVE demo you refresh the SPA's token instead.
function Show-Refresh {
  Write-Host "`n=== Demo E · REFRESH TOKEN (silent renewal) ===" -ForegroundColor Cyan
  $first = Invoke-RestMethod -Method Post -Uri "$Realm/token" -Body @{
    grant_type='password'; client_id='kt-web'; client_secret='kt-web-secret'
    username='farhaan'; password='Passw0rd!'; scope='openid'
  }
  Write-Host "Got an initial refresh_token (first 24 chars): $($first.refresh_token.Substring(0,24))..." -ForegroundColor DarkGray
  Start-Sleep -Seconds 1
  $second = Invoke-RestMethod -Method Post -Uri "$Realm/token" -Body @{
    grant_type='refresh_token'; client_id='kt-web'; client_secret='kt-web-secret'
    refresh_token=$first.refresh_token
  }
  Write-Host "`nRefreshed. NEW access token issued with no user interaction." -ForegroundColor Green
  Write-Host "Old refresh_token first 24: $($first.refresh_token.Substring(0,24))..."
  Write-Host "New refresh_token first 24: $($second.refresh_token.Substring(0,24))...  <- rotated (different)" -ForegroundColor Yellow
}

# ---- BONUS: ROPC — the DEPRECATED password grant (teach why it's bad) -------
function Show-Ropc {
  Write-Host "`n=== BONUS · RESOURCE OWNER PASSWORD CREDENTIALS (DEPRECATED) ===" -ForegroundColor Cyan
  Write-Host "The app collects the user's ACTUAL password and sends it here. This is the" -ForegroundColor DarkGray
  Write-Host "anti-pattern OAuth exists to kill: the app sees the password, and MFA is bypassed." -ForegroundColor DarkGray
  $r = Invoke-RestMethod -Method Post -Uri "$Realm/token" -Body @{
    grant_type='password'; client_id='kt-web'; client_secret='kt-web-secret'
    username='farhaan'; password='Passw0rd!'; scope='openid'
  }
  Write-Host "It works (Keycloak has it enabled for this demo) — and THAT is the problem." -ForegroundColor Red
  Write-Host "Removed in OAuth 2.1. Use Authorization Code + PKCE instead." -ForegroundColor Red
}

Write-Host "Loaded. Functions: Show-ClientCredentials, Show-DeviceFlow, Show-Refresh, Show-Ropc" -ForegroundColor Green
