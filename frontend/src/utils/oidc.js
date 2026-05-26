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

const SS_VERIFIER = 'oidc_pkce_verifier'
const SS_STATE    = 'oidc_state'
const SS_FROM     = 'oidc_post_login_from'

/** Read OIDC config out of Vite env (build-time). */
export function oidcConfig() {
  const env = import.meta.env
  return {
    enabled:     env.VITE_OIDC_ENABLED === 'true',
    issuer:      env.VITE_OIDC_ISSUER,
    clientId:    env.VITE_OIDC_CLIENT_ID,
    redirectUri: env.VITE_OIDC_REDIRECT_URI,
    scopes:      env.VITE_OIDC_SCOPES || 'openid profile email'
  }
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
