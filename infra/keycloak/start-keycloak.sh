#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# Local Keycloak launcher for the access-matrix project.
# See start-keycloak.bat for the Windows equivalent (same defaults).
# -----------------------------------------------------------------------------
set -euo pipefail

: "${KEYCLOAK_HOME:=/c/SERVER/keycloak-26.6.2}"

if [[ ! -x "$KEYCLOAK_HOME/bin/kc.sh" ]]; then
    echo "[ERROR] Keycloak not found at $KEYCLOAK_HOME/bin/kc.sh" >&2
    echo "Set KEYCLOAK_HOME or edit this script. Download from" >&2
    echo "  https://www.keycloak.org/downloads" >&2
    exit 1
fi

export KC_DB=postgres
export KC_DB_URL="${KC_DB_URL:-jdbc:postgresql://127.0.0.1:5432/new_inntouch_core?currentSchema=keycloak}"
export KC_DB_USERNAME="${KC_DB_USERNAME:-postgres}"
export KC_DB_PASSWORD="${KC_DB_PASSWORD:-abcd@1234}"

export KC_HTTP_PORT=8180
export KC_HOSTNAME_STRICT=false

# Dev-only admin bootstrap. NEVER use these credentials anywhere reachable.
export KEYCLOAK_ADMIN=admin
export KEYCLOAK_ADMIN_PASSWORD=admin

cat <<EOF

=== Starting Keycloak ===
  Home:        $KEYCLOAK_HOME
  Database:    $KC_DB_URL
  Admin URL:   http://localhost:$KC_HTTP_PORT/admin   (admin / admin)
  Realm UI:    http://localhost:$KC_HTTP_PORT

EOF

# --import-realm auto-loads any *.json placed under $KEYCLOAK_HOME/data/import.
exec "$KEYCLOAK_HOME/bin/kc.sh" start-dev --import-realm
