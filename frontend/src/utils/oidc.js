/**
 * OIDC (OpenID Connect) Authorization Code + PKCE flow against Keycloak.
 *
 * Two entry points:
 *   beginLogin()          - kick off the redirect to the IdP authorize URL
 *   handleCallback(query) - exchange the returned code for an access token
 *
 * State is stashed in sessionStorage (NOT localStorage) so it survives the
 * IdP round-trip but doesn't linger past the tab session.
 */

import { newCodeVerifier, deriveCodeChallenge, newState } from './pkce'
import { currentTenant } from './tenant'

const SS_VERIFIER = 'oidc_pkce_verifier'
const SS_STATE    = 'oidc_state'
const SS_FROM     = 'oidc_post_login_from'

/**
 * Read OIDC config. Mix of build-time (`import.meta.env`) and runtime
 * (current subdomain → realm, current origin → redirect URI) so the same
 * SPA build can serve multiple tenants over different subdomains.
 *
 * Precedence:
 *
 *   issuer
 *     - `VITE_OIDC_ISSUER` if set         (legacy single-realm pinning)
 *     - else `VITE_OIDC_ISSUER_BASE/realms/${currentTenant()}`  (multi-realm)
 *
 *   redirectUri
 *     - `VITE_OIDC_REDIRECT_URI` if set   (pin behind a proxy)
 *     - else `${window.location.origin}/sso/callback`           (per-host)
 *
 * The Keycloak client must have valid-redirect-uris that match — for the
 * subdomain rollout, register a wildcard such as
 * `https://*.access-matrix.com/sso/callback`.
 */
export function oidcConfig() {
  const env = import.meta.env
  const tenant = currentTenant()
  let issuer = env.VITE_OIDC_ISSUER
  if (!issuer) {
    const base = (env.VITE_OIDC_ISSUER_BASE || '').replace(/\/$/, '')
    if (base) issuer = `${base}/realms/${tenant}`
  }
  const redirectUri = env.VITE_OIDC_REDIRECT_URI
    || (typeof window !== 'undefined' ? `${window.location.origin}/sso/callback` : '')
  return {
    enabled:     env.VITE_OIDC_ENABLED === 'true',
    issuer,
    clientId:    env.VITE_OIDC_CLIENT_ID,
    redirectUri,
    scopes:      env.VITE_OIDC_SCOPES || 'openid profile email',
    tenant
  }
}

/**
 * URL of the Keycloak built-in self-service account console — the user can
 * change their password, email, MFA settings, view sessions, etc. Each realm
 * has its own at /realms/&lt;realm&gt;/account/. We derive the realm from the
 * issuer URL so we don't need a separate VITE_ var.
 */
export function keycloakAccountUrl() {
  const cfg = oidcConfig()
  if (!cfg.enabled || !cfg.issuer) return null
  return `${cfg.issuer}/account/`
}

/**
 * RP-Initiated Logout URL (OIDC spec). Hits Keycloak's end_session_endpoint
 * so the IdP wipes its own session cookie; without this, the next
 * "Sign in with SSO" silent-logins via Keycloak's still-valid cookie
 * and the user can't actually log out.
 *
 * Requires the id_token from the original /token exchange — Keycloak
 * uses it to confirm WHICH session to end. Falls back to a plain
 * post_logout_redirect_uri navigation if id_token isn't available
 * (e.g. legacy session predating this fix); Keycloak 26 may then
 * present a confirmation page asking the user to confirm logout.
 *
 * @param idToken      the id_token from the last successful login (nullable)
 * @param postLogoutTo absolute URL to land on after logout (e.g. /login)
 */
export function keycloakLogoutUrl(idToken, postLogoutTo) {
  const cfg = oidcConfig()
  if (!cfg.enabled || !cfg.issuer) return null
  const params = new URLSearchParams({
    post_logout_redirect_uri: postLogoutTo
  })
  if (idToken) {
    params.append('id_token_hint', idToken)
  } else {
    // Without id_token_hint, Keycloak needs client_id to know which client's
    // post-logout redirect list to validate against.
    params.append('client_id', cfg.clientId)
  }
  return `${cfg.issuer}/protocol/openid-connect/logout?${params}`
}

/**
 * URL of the Keycloak built-in "forgot password" flow. Lands the user at
 * the email-collection page; Keycloak handles the rest (sending the
 * reset email, validating the link, prompting for a new password).
 * Returns to redirect_uri after success.
 */
export function keycloakForgotPasswordUrl() {
  const cfg = oidcConfig()
  if (!cfg.enabled || !cfg.issuer || !cfg.clientId) return null
  const params = new URLSearchParams({
    client_id:     cfg.clientId,
    redirect_uri:  cfg.redirectUri,
    response_type: 'code',
    scope:         cfg.scopes,
    // tab_id-less reset-credentials shortcut: append &kc_action=reset
    // to a normal authorize URL → Keycloak triggers the reset-password
    // required action immediately instead of asking for credentials.
    kc_action:     'reset-credentials'
  })
  return `${cfg.issuer}/protocol/openid-connect/auth?${params}`
}

/** Where the user wanted to land — preserved through the IdP round-trip. */
export function stashReturnTo(path) {
  if (path) sessionStorage.setItem(SS_FROM, path)
}

export function popReturnTo() {
  const v = sessionStorage.getItem(SS_FROM)
  sessionStorage.removeItem(SS_FROM)
  return v || '/'
}

/**
 * Generate PKCE + state, redirect the browser to Keycloak's /authorize.
 * Returns a Promise that never resolves (we navigate away first).
 */
export async function beginLogin() {
  const cfg = oidcConfig()
  if (!cfg.enabled) throw new Error('OIDC disabled (VITE_OIDC_ENABLED=false)')
  if (!cfg.issuer || !cfg.clientId || !cfg.redirectUri) {
    throw new Error('OIDC misconfigured — check VITE_OIDC_* env vars')
  }

  const verifier  = newCodeVerifier()
  const challenge = await deriveCodeChallenge(verifier)
  const state     = newState()

  sessionStorage.setItem(SS_VERIFIER, verifier)
  sessionStorage.setItem(SS_STATE,    state)

  const params = new URLSearchParams({
    response_type:         'code',
    client_id:             cfg.clientId,
    redirect_uri:          cfg.redirectUri,
    scope:                 cfg.scopes,
    state:                 state,
    code_challenge:        challenge,
    code_challenge_method: 'S256'
  })

  window.location.assign(`${cfg.issuer}/protocol/openid-connect/auth?${params}`)
  // We've left the SPA — return a never-resolving promise so callers don't
  // race with the navigation.
  return new Promise(() => {})
}

/**
 * Handle the IdP redirect back to /sso/callback. Validates state, swaps the
 * one-time code for an access token via the OIDC token endpoint, and returns
 * { accessToken, idToken, refreshToken, expiresIn }.
 *
 * Throws on:
 *   - missing or mismatched state (CSRF)
 *   - missing code_verifier (browser cleared sessionStorage)
 *   - HTTP error from /token (network or invalid_grant)
 */
export async function handleCallback(query) {
  const cfg = oidcConfig()
  if (!cfg.enabled) throw new Error('OIDC disabled — unexpected callback')

  const code  = query.code
  const state = query.state
  if (!code)  throw new Error('Authorization code missing from callback')
  if (!state) throw new Error('OAuth state missing from callback')

  const expectedState = sessionStorage.getItem(SS_STATE)
  const verifier      = sessionStorage.getItem(SS_VERIFIER)
  sessionStorage.removeItem(SS_STATE)
  sessionStorage.removeItem(SS_VERIFIER)

  if (state !== expectedState) {
    throw new Error('OAuth state mismatch — possible CSRF; refusing to exchange code')
  }
  if (!verifier) {
    throw new Error('PKCE verifier lost — please try logging in again')
  }

  const body = new URLSearchParams({
    grant_type:    'authorization_code',
    client_id:     cfg.clientId,
    code:          code,
    redirect_uri:  cfg.redirectUri,
    code_verifier: verifier
  })

  const res = await fetch(`${cfg.issuer}/protocol/openid-connect/token`, {
    method:  'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body
  })
  if (!res.ok) {
    let detail = ''
    try { detail = (await res.json()).error_description || (await res.text()) } catch { /* noop */ }
    throw new Error(`OIDC token exchange failed (${res.status}) ${detail}`)
  }
  const json = await res.json()
  return {
    accessToken:  json.access_token,
    idToken:      json.id_token,
    refreshToken: json.refresh_token,
    expiresIn:    json.expires_in
  }
}
