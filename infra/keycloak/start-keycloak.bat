@echo off
REM ----------------------------------------------------------------------------
REM Local Keycloak launcher for the access-matrix project.
REM
REM Talks to the same Postgres instance as the application, isolated by schema
REM (`keycloak`). The dev-mode admin credentials are admin/admin — DO NOT use
REM this script in any environment reachable from outside your machine.
REM
REM Requires:
REM   - JDK 17+ on PATH (you already have JDK 25 for the backend)
REM   - Keycloak 26+ extracted somewhere on disk
REM   - Postgres running on 127.0.0.1:5432 with database `new_inntouch_core`
REM     and a schema named `keycloak` (CREATE SCHEMA IF NOT EXISTS keycloak;)
REM
REM Set KEYCLOAK_HOME to your install directory before first run, e.g. one of:
REM   set KEYCLOAK_HOME=C:\SERVER\keycloak-26.6.2
REM   (or edit the default below)
REM ----------------------------------------------------------------------------

if "%KEYCLOAK_HOME%"=="" set KEYCLOAK_HOME=C:\SERVER\keycloak-26.6.2

if not exist "%KEYCLOAK_HOME%\bin\kc.bat" (
    echo [ERROR] Keycloak not found at %KEYCLOAK_HOME%\bin\kc.bat
    echo Set KEYCLOAK_HOME or edit this script. Download Keycloak from
    echo   https://www.keycloak.org/downloads
    exit /b 1
)

REM --- Database (override CORE_DB_* if your local PG differs) ---
if "%KC_DB_URL%"==""      set KC_DB_URL=jdbc:postgresql://127.0.0.1:5432/new_inntouch_core?currentSchema=keycloak
if "%KC_DB_USERNAME%"=="" set KC_DB_USERNAME=postgres
if "%KC_DB_PASSWORD%"=="" set KC_DB_PASSWORD=abcd@1234
set KC_DB=postgres

REM --- HTTP ---
set KC_HTTP_PORT=8180
set KC_HOSTNAME_STRICT=false
REM Bind to the IPv6 wildcard. Quarkus / Vert.x set IPV6_V6ONLY=false on the
REM listening socket, so binding to :: ends up dual-stacked (accepts both v6
REM ::1 and v4 127.0.0.1 hits). Without this, start-dev binds to "localhost"
REM which on Windows resolves to 127.0.0.1 only — Chrome, which prefers IPv6
REM via Happy Eyeballs, then hits the v6 [::1]:8180, gets a connection refused
REM and reports "SSO unreachable" instead of falling back to v4. Edge tolerates
REM this; Chrome doesn't.
set KC_HTTP_HOST=::

REM --- Bootstrap admin (dev only) ---
REM Keycloak 26 split admin bootstrap into a dedicated cmd; the env vars below
REM still work for backward compatibility with start-dev.
set KEYCLOAK_ADMIN=admin
set KEYCLOAK_ADMIN_PASSWORD=admin

REM --- Sync custom themes (access-matrix) into $KEYCLOAK_HOME/themes ---
REM Keycloak only looks at its own themes folder; we keep the source of
REM truth under infra/keycloak/themes/ (commits + reviewable) and copy
REM on each launch. xcopy with /D only updates changed files so this is
REM essentially free after the first run.
set THEMES_SRC=%~dp0themes
if exist "%THEMES_SRC%" (
    echo Syncing themes from %THEMES_SRC% to %KEYCLOAK_HOME%\themes ...
    xcopy "%THEMES_SRC%\*" "%KEYCLOAK_HOME%\themes\" /E /I /Y /D > nul
)

REM --- Sync realm exports into $KEYCLOAK_HOME/data/import ---
REM --import-realm reads from data\import only, so the realm JSONs in
REM infra\keycloak\realms\ need to land there before kc.bat fires. Keeps
REM realm definitions reviewable in-repo while letting a fresh clone boot
REM the right realms on first launch without anyone touching KC console.
set REALMS_SRC=%~dp0realms
if exist "%REALMS_SRC%\*.json" (
    if not exist "%KEYCLOAK_HOME%\data\import" mkdir "%KEYCLOAK_HOME%\data\import"
    echo Syncing realm imports from %REALMS_SRC% to %KEYCLOAK_HOME%\data\import ...
    xcopy "%REALMS_SRC%\*.json" "%KEYCLOAK_HOME%\data\import\" /Y /D > nul
)

echo.
echo === Starting Keycloak ===
echo   Home:        %KEYCLOAK_HOME%
echo   Database:    %KC_DB_URL%
echo   Admin URL:   http://localhost:%KC_HTTP_PORT%/admin   (admin / admin)
echo   Realm UI:    http://localhost:%KC_HTTP_PORT%
echo.

REM --import-realm imports any *.json under $KEYCLOAK_HOME/data/import on startup.
REM Note: --import-realm only inserts when the realm doesn't already exist; to
REM apply changes (theme switch / new client / mapper edits) to an existing
REM realm, delete it from the admin console first or use partial-import.
"%KEYCLOAK_HOME%\bin\kc.bat" start-dev --import-realm
