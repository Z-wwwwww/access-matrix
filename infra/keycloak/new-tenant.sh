#!/usr/bin/env bash
# Clone demo-realm.json into <name>-realm.json with the realm name and
# tid hardcoded-claim-mapper retargeted. See new-tenant.ps1 header for the
# full rationale; this is just the unix companion.

set -euo pipefail

name="${1:-}"
# Optional human-readable realm label; defaults to the tenant name. Without it the
# clone keeps "Demo Tenant" in Keycloak's realm picker, which is actively confusing
# when the whole point is telling tenants apart.
display="${2:-$name}"
if [[ -z "$name" || ! "$name" =~ ^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$ ]]; then
  echo "usage: $0 <tenant-name> [display-name]  (tenant-name = lowercase RFC1035 label)" >&2
  exit 1
fi
if [[ "$name" == "demo" ]]; then
  echo "'demo' already exists - edit demo-realm.json directly instead" >&2
  exit 1
fi
if [[ "$name" == "system" ]]; then
  echo "'system' is reserved for platform-ops realm - choose a different name" >&2
  exit 1
fi

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
src="$here/realms/demo-realm.json"
dst="$here/realms/${name}-realm.json"

[[ -f "$src" ]] || { echo "source realm not found at $src" >&2; exit 1; }
[[ -f "$dst" ]] && { echo "$dst already exists - delete it first if you really want to overwrite" >&2; exit 1; }

# Use perl rather than sed for cross-platform regex semantics (macOS sed
# differs from GNU sed on -i and on \s). Surgical replacements only — a blanket
# s/demo/$name/ would damage unrelated keys. Must stay in lock-step with
# KeycloakRealmService.renderRealmJson, which is the same logic in JVM-land.
#
# The /realms/demo/account/ + /admin/demo/console/ replaces fix the built-in
# client URLs (account/account-console redirectUris + baseUrl, realm admin
# console) which embed the realm name; left at "demo" the new realm's account
# console rejects its own redirect_uri.
#
# The final pass regenerates every UUID. The template is demo's realm EXPORT, so
# it carries demo's realm id plus every nested role / client / mapper id — and
# those are globally-unique PRIMARY KEYS in Keycloak. Left as-is the clone
# collides with the existing demo realm (the JVM path documents the same thing:
# kc.realms().create() answers 409 "already exists"; an --import-realm bootstrap
# hits the realm PK). The remap is consistent — same source id maps to the same
# fresh id everywhere it appears — so internal references stay intact.
perl -pe 's/"realm":\s*"demo"/"realm":  "'"$name"'"/' "$src"   | perl -pe 's/"displayName":\s*"Demo Tenant"/"displayName":  "'"$display"'"/'   | perl -pe 's/"claim\.value":\s*"demo"/"claim.value":  "'"$name"'"/'   | perl -pe 's{/realms/demo/account/}{/realms/'"$name"'/account/}g'   | perl -pe 's{/admin/demo/console/}{/admin/'"$name"'/console/}g'   | perl -pe 'BEGIN { %seen = ();
                      sub fresh_uuid {
                        my $h = join "", map { sprintf "%02x", int(rand(256)) } (1 .. 16);
                        substr($h, 12, 1) = "4";                                  # version 4
                        substr($h, 16, 1) = (qw(8 9 a b))[int(rand(4))];           # variant
                        return join "-", substr($h,0,8), substr($h,8,4),
                                         substr($h,12,4), substr($h,16,4), substr($h,20,12);
                      } }
              s/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})/$seen{$1} ||= fresh_uuid()/ge'   > "$dst"

echo "Wrote $dst"
echo
echo "Next steps:"
echo "  1. Restart Keycloak with --import-realm to load it (start-keycloak.sh already passes the flag)"
echo "  2. Verify in admin console: http://localhost:8180/admin -> realm picker -> '$name'"
echo "  3. Provision the first admin user via Users tab or kcadm"
echo "  4. SPA reaches this realm via:"
echo "       https://$name.access-matrix.com/    (production subdomain)"
echo "       http://localhost:5273/?tenant=$name (dev override)"
