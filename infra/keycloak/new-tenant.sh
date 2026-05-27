#!/usr/bin/env bash
# Clone demo-realm.json into <name>-realm.json with the realm name and
# tid hardcoded-claim-mapper retargeted. See new-tenant.ps1 header for the
# full rationale; this is just the unix companion.

set -euo pipefail

name="${1:-}"
if [[ -z "$name" || ! "$name" =~ ^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$ ]]; then
  echo "usage: $0 <tenant-name>  (lowercase RFC1035 label)" >&2
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
# differs from GNU sed on -i and on \s). Two surgical replacements only.
perl -pe 's/"realm":\s*"demo"/"realm":  "'"$name"'"/' "$src" \
  | perl -pe 's/"claim\.value":\s*"demo"/"claim.value":  "'"$name"'"/' \
  > "$dst"

echo "Wrote $dst"
echo
echo "Next steps:"
echo "  1. Restart Keycloak with --import-realm to load it (start-keycloak.sh already passes the flag)"
echo "  2. Verify in admin console: http://localhost:8180/admin -> realm picker -> '$name'"
echo "  3. Provision the first admin user via Users tab or kcadm"
echo "  4. SPA reaches this realm via:"
echo "       https://$name.access-matrix.com/    (production subdomain)"
echo "       http://localhost:5273/?tenant=$name (dev override)"
