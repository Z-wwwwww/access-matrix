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
# Bind to the IPv6 wildcard — see start-keycloak.bat for the long version.
# tl;dr: Quarkus disables IPV6_V6ONLY so `::` listens dual-stack, which keeps
# Chrome (IPv6-first) happy alongside curl/Edge (IPv4 fallback).
export KC_HTTP_HOST="::"

# Dev-only admin bootstrap. NEVER use these credentials anywhere reachable.
export KEYCLOAK_ADMIN=admin
export KEYCLOAK_ADMIN_PASSWORD=admin

# Sync custom themes (access-matrix) into $KEYCLOAK_HOME/themes — Keycloak
# only looks at its own themes folder, but we keep the source of truth in
# infra/keycloak/themes/ so it's reviewable + version-controlled. rsync
# with -u only copies changed files, so this is effectively free on warm
# launches.
THEMES_SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/themes"
if [[ -d "$THEMES_SRC" ]]; then
    echo "Syncing themes from $THEMES_SRC to $KEYCLOAK_HOME/themes ..."
    if command -v rsync > /dev/null; then
        rsync -a --update "$THEMES_SRC/" "$KEYCLOAK_HOME/themes/"
    else
        cp -RuT "$THEMES_SRC" "$KEYCLOAK_HOME/themes"
    fi
fi

# Sync realm exports into $KEYCLOAK_HOME/data/import — that's the only
# place --import-realm looks. Keeps realm definitions reviewable in-repo
# while letting a fresh clone boot the right realms on first launch
# without anyone touching the KC admin console.
REALMS_SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/realms"
if [[ -d "$REALMS_SRC" ]] && compgen -G "$REALMS_SRC/*.json" > /dev/null; then
    mkdir -p "$KEYCLOAK_HOME/data/import"
    echo "Syncing realm imports from $REALMS_SRC to $KEYCLOAK_HOME/data/import ..."
    if command -v rsync > /dev/null; then
        rsync -a --update "$REALMS_SRC/"*.json "$KEYCLOAK_HOME/data/import/"
    else
        cp -u "$REALMS_SRC/"*.json "$KEYCLOAK_HOME/data/import/"
    fi
fi

cat <<EOF

=== Starting Keycloak ===
  Home:        $KEYCLOAK_HOME
  Database:    $KC_DB_URL
  Admin URL:   http://localhost:$KC_HTTP_PORT/admin   (admin / admin)
  Realm UI:    http://localhost:$KC_HTTP_PORT

EOF

# --import-realm auto-loads any *.json placed under $KEYCLOAK_HOME/data/import.
exec "$KEYCLOAK_HOME/bin/kc.sh" start-dev --import-realm
