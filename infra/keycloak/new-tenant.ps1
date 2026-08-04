<#
.SYNOPSIS
    Clone the committed demo-realm.json into a new <name>-realm.json
    with the realm + tid hardcoded-claim-mapper retargeted at <name>.

.DESCRIPTION
    Multi-tenant convention in this project:
      realm name == tenant id == subdomain label
    so adding a tenant "acme" means dropping infra/keycloak/realms/acme-realm.json
    that's a copy of demo-realm.json with the realm-scoped strings retargeted:
      "realm": "demo"                  -> "realm": "acme"
      "displayName": "Demo Tenant"     -> "displayName": <-DisplayName>
      "claim.value": "demo"            -> "claim.value": "acme"   (the tid mapper)
      /realms/demo/account/            -> /realms/acme/account/   (account console)
      /admin/demo/console/             -> /admin/acme/console/    (realm admin console)
    ...plus a regeneration of every UUID, because the template is demo's realm
    EXPORT and those ids are Keycloak PRIMARY KEYS (see the comment on that pass).

    This must stay in lock-step with KeycloakRealmService.renderRealmJson, which
    performs the same steps when a tenant is created from the platform console.

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
    [Parameter(Mandatory=$true)][string]$Name,
    # Human-readable realm label shown in Keycloak's realm picker. Defaults to
    # -Name; without it the clone keeps "Demo Tenant", which is actively
    # confusing when the whole point is telling tenants apart.
    [Parameter(Mandatory=$false)][string]$DisplayName
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($DisplayName)) { $DisplayName = $Name }

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

# Surgical replace - only the specific strings we know need to change.
# Avoid a blanket s/demo/$Name/ because realm JSON contains lots of
# unrelated strings (default-roles-*, etc. - leftover from the demo template).
# Must stay in lock-step with KeycloakRealmService.renderRealmJson, which is
# the same logic in JVM-land.
#
# The /realms/demo/account/ + /admin/demo/console/ replaces fix the built-in
# client URLs (redirectUris/baseUrl embed the realm name); left at "demo" the
# new realm's account console rejects its own redirect_uri.
$json = Get-Content $src -Raw
$json = $json -replace '"realm":\s*"demo"',              ('"realm":  "{0}"' -f $Name)
$json = $json -replace '"displayName":\s*"Demo Tenant"', ('"displayName":  "{0}"' -f $DisplayName)
$json = $json -replace '"claim\.value":\s*"demo"',       ('"claim.value":  "{0}"' -f $Name)
$json = $json.Replace('/realms/demo/account/',           ('/realms/{0}/account/' -f $Name))
$json = $json.Replace('/admin/demo/console/',            ('/admin/{0}/console/'  -f $Name))

# Regenerate every UUID. The template is demo's realm EXPORT, so it carries
# demo's realm id plus every nested role / client / mapper id - and those are
# globally-unique PRIMARY KEYS in Keycloak. Left as-is the clone collides with
# the existing demo realm (the JVM path documents the same thing:
# kc.realms().create() answers 409 "already exists"; an --import-realm bootstrap
# hits the realm PK). Remap consistently - same source id to the same fresh id
# everywhere it appears - so internal references stay intact.
$uuidMap = @{}
$uuidRe  = '[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}'
$json = [regex]::Replace($json, $uuidRe, {
    param($m)
    $k = $m.Value
    if (-not $uuidMap.ContainsKey($k)) { $uuidMap[$k] = [guid]::NewGuid().ToString() }
    $uuidMap[$k]
})

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
