<#
.SYNOPSIS
    Clone the committed demo-realm.json into a new <name>-realm.json
    with the realm + tid hardcoded-claim-mapper retargeted at <name>.

.DESCRIPTION
    Multi-tenant convention in this project:
      realm name == tenant id == subdomain label
    so adding a tenant "acme" means dropping infra/keycloak/realms/acme-realm.json
    that's a copy of demo-realm.json with two strings swapped:
      "realm": "demo"             -> "realm": "acme"
      "claim.value": "demo"       -> "claim.value": "acme"     (the tid mapper)

    Everything else (client config, default scopes, etc.) carries over.
    Re-import (`kc.bat start --import-realm`) picks the new file up
    automatically on the next Keycloak restart.

.PARAMETER Name
    Tenant id. Lowercase RFC 1035 label: starts alphanumeric, then
    alphanumerics or hyphens, 1-63 chars.

.EXAMPLE
    .\infra\keycloak\new-tenant.ps1 -Name acme
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$Name
)

$ErrorActionPreference = 'Stop'

if ($Name -notmatch '^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$') {
    Write-Error "invalid tenant name '$Name' - must be a lowercase RFC1035 label"
    exit 1
}
if ($Name -eq 'demo') {
    Write-Error "'demo' already exists - edit demo-realm.json directly instead"
    exit 1
}
if ($Name -eq 'system') {
    Write-Error "'system' is reserved for platform-ops realm - choose a different name"
    exit 1
}

$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$src  = Join-Path $here 'realms\demo-realm.json'
$dst  = Join-Path $here ("realms\{0}-realm.json" -f $Name)

if (-not (Test-Path $src)) {
    Write-Error "source realm not found at $src"
    exit 1
}
if (Test-Path $dst) {
    Write-Error "$dst already exists - delete it first if you really want to overwrite"
    exit 1
}

# Surgical replace - only the two specific lines we know need to change.
# Avoid a blanket s/demo/$Name/ because realm JSON contains lots of
# unrelated strings (default-roles-*, etc. — leftover from the demo template).
$json = Get-Content $src -Raw
$json = $json -replace '"realm":\s*"demo"',          ('"realm":  "{0}"' -f $Name)
$json = $json -replace '"claim\.value":\s*"demo"',   ('"claim.value":  "{0}"' -f $Name)

Set-Content -Path $dst -Value $json -Encoding UTF8 -NoNewline

Write-Host "Wrote $dst" -ForegroundColor Green
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "  1. Restart Keycloak with --import-realm to load it" -ForegroundColor Cyan
Write-Host "     (start-keycloak.bat already passes the flag)" -ForegroundColor Cyan
Write-Host "  2. Verify in admin console: http://localhost:8180/admin -> realm picker -> '$Name'" -ForegroundColor Cyan
Write-Host "  3. Provision the first admin user via Users tab or kcadm" -ForegroundColor Cyan
Write-Host "  4. On a multi-tenant deploy, the SPA reaches this realm via:" -ForegroundColor Cyan
Write-Host "       https://$Name.access-matrix.com/   (production subdomain)" -ForegroundColor Cyan
Write-Host "       http://localhost:5273/?tenant=$Name   (dev override)" -ForegroundColor Cyan
