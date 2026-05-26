/**
 * PKCE (Proof Key for Code Exchange — RFC 7636) helpers.
 *
 * Required for the OIDC Authorization Code flow against a "public client"
 * (no client secret). The flow:
 *
 *   1. browser → create random `code_verifier` (43-128 chars, URL-safe)
 *   2. browser → derive `code_challenge` = base64url(SHA-256(verifier))
 *   3. browser → send challenge with /authorize redirect
 *   4. IdP returns ?code=... after user login
 *   5. browser → POST /token with code + the ORIGINAL verifier
 *   6. IdP verifies SHA-256(verifier) == previously-sent challenge
 *
 * Without PKCE a public client is vulnerable to anyone who intercepts
 * the redirect (e.g. malicious browser extension) being able to swap
 * the code for a token. With PKCE they don't have the verifier.
 */

const VERIFIER_BYTES = 32   // → 43 base64url chars (well within RFC's 43-128).

/** Crypto-safe random bytes → base64url. */
function randomBase64Url(bytes) {
  const buf = new Uint8Array(bytes)
  crypto.getRandomValues(buf)
  return toBase64Url(buf)
}

function toBase64Url(bytes) {
  // btoa wants a "binary string" — feed it the raw bytes.
  let bin = ''
  for (const b of bytes) bin += String.fromCharCode(b)
  return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

export function newCodeVerifier() {
  return randomBase64Url(VERIFIER_BYTES)
}

export async function deriveCodeChallenge(verifier) {
  const data = new TextEncoder().encode(verifier)
  const hash = await crypto.subtle.digest('SHA-256', data)
  return toBase64Url(new Uint8Array(hash))
}

/** Opaque random nonce used for the OAuth `state` param (CSRF guard). */
export function newState() {
  return randomBase64Url(16)
}
