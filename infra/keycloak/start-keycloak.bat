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

REM --- Bootstrap admin (dev only) ---
REM Keycloak 26 split admin bootstrap into a dedicated cmd; the env vars below
REM still work for backward compatibility with start-dev.
set KEYCLOAK_ADMIN=admin
set KEYCLOAK_ADMIN_PASSWORD=admin

echo.
echo === Starting Keycloak ===
echo   Home:        %KEYCLOAK_HOME%
echo   Database:    %KC_DB_URL%
echo   Admin URL:   http://localhost:%KC_HTTP_PORT%/admin   (admin / admin)
echo   Realm UI:    http://localhost:%KC_HTTP_PORT%
echo.

REM --import-realm imports any *.json under $KEYCLOAK_HOME/data/import on startup.
REM We don't yet have a realm-export.json committed; once the dev realm is
REM configured via the admin UI, export it from the Realm Settings > Action
REM menu and drop it into infra/keycloak/realms/, then symlink that directory
REM into Keycloak's data/import for auto-load.
"%KEYCLOAK_HOME%\bin\kc.bat" start-dev --import-realm
